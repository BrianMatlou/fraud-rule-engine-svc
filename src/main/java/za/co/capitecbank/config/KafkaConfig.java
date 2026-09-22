package za.co.capitecbank.config;

import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.DeserializationException;
import za.co.capitecbank.dto.TransactionEventDto;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {

    @Value("${fraud-check.kafka.topic}")
    private String topic;

    @Value("${fraud-check.kafka.dead-letter-topic}")
    private String deadLetterTopic;

    @Value("${fraud-check.kafka.retry-backoff:1000}")
    private Long retryBackoff;

    @Value("${fraud-check.kafka.retry-attempts:3}")
    private Integer retryAttempts;


    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TransactionEventDto> kafkaListenerContainerFactory(
            ConsumerFactory<String, TransactionEventDto> consumerFactory,
            KafkaTemplate<Object, Object> kafkaTemplate) {

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ignored) -> new TopicPartition(deadLetterTopic, record.partition()));

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(retryBackoff, retryAttempts));

        errorHandler.addNotRetryableExceptions(IllegalStateException.class, DeserializationException.class);

        ConcurrentKafkaListenerContainerFactory<String, TransactionEventDto> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }

    /* ===================================Create the topics =====================================*/

    @Bean
    public NewTopic transactionEventsTopic() {
        return TopicBuilder.name(topic).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic transactionEventsDltTopic() {
        return TopicBuilder.name(deadLetterTopic).partitions(1).replicas(1).build();
    }
}