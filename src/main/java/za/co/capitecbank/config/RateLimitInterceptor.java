package za.co.capitecbank.config;

import tools.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import za.co.capitecbank.dto.ErrorResponse;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
            return true;
        }

        String sub = jwtAuth.getToken().getSubject();
        String uri = request.getRequestURI();

        String group;
        RateLimitProperties.BucketConfig config;
        if (uri.startsWith("/api/v1/fraud")) {
            group = "fraud";
            config = properties.fraudCheck();
        } else if (uri.startsWith("/api/v1/rules")) {
            group = "rules";
            config = properties.rules();
        } else {
            return true;
        }

        Bucket bucket = buckets.computeIfAbsent(group + ":" + sub, k -> buildBucket(config));

        if (bucket.tryConsume(1)) {
            return true;
        }

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", String.valueOf(config.refillPeriodSeconds()));
        objectMapper.writeValue(response.getWriter(), new ErrorResponse(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                "Rate limit exceeded. Retry after " + config.refillPeriodSeconds() + " seconds.",
                OffsetDateTime.now(),
                uri
        ));
        return false;
    }

    private Bucket buildBucket(RateLimitProperties.BucketConfig config) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(config.capacity())
                .refillIntervally(config.refillTokens(), Duration.ofSeconds(config.refillPeriodSeconds()))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }
}
