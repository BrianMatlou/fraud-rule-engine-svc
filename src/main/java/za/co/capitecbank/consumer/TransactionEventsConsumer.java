package za.co.capitecbank.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import za.co.capitecbank.config.AuditContext;
import za.co.capitecbank.dto.TransactionEventDto;
import za.co.capitecbank.service.FraudCheckService;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionEventsConsumer {

    private final FraudCheckService fraudCheckService;

    @KafkaListener(
            topics = "transaction-events",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeTransactionEvents(
            @Payload TransactionEventDto transactionEvent,
            @Header(name = "x-actor-id") byte[] actorIdHeader
    ) {
        try {
            AuditContext.set(new String(actorIdHeader, StandardCharsets.UTF_8));
            this.fraudCheckService.performCheck(transactionEvent);
            log.atInfo().addKeyValue("Transaction-Id",transactionEvent.transactionId()).log("Transaction event processed");
        } catch (Exception exception) {
            log.atError().setCause(exception).log("Error processing transaction event: {}", transactionEvent);
            throw exception;
        } finally {
            AuditContext.clear();
        }
    }
}