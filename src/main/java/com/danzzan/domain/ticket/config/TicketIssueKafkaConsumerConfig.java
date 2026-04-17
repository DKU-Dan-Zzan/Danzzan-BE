package com.danzzan.domain.ticket.config;

import com.danzzan.domain.ticket.consumer.exception.NonRetryableTicketIssueException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.util.backoff.FixedBackOff;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
public class TicketIssueKafkaConsumerConfig {

    @Bean
    public DefaultErrorHandler ticketIssueKafkaErrorHandler(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${app.ticketing.kafka.topics.issue-requested-dlq:ticket.issue.requested.v1.dlq}") String dltTopic
    ) {
        DefaultErrorHandler errorHandler = new DefaultErrorHandler((record, ex) ->
                kafkaTemplate.send(dltTopic, String.valueOf(record.key()), toDlqPayload(objectMapper, record, ex)),
                new FixedBackOff(1000L, 2L)
        );
        errorHandler.addNotRetryableExceptions(NonRetryableTicketIssueException.class);
        errorHandler.setCommitRecovered(true);
        return errorHandler;
    }

    @Bean(name = "ticketIssueKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, String> ticketIssueKafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            DefaultErrorHandler ticketIssueKafkaErrorHandler
    ) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(ticketIssueKafkaErrorHandler);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        return factory;
    }

    private String toDlqPayload(ObjectMapper objectMapper, ConsumerRecord<?, ?> record, Exception ex) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("originalTopic", record.topic());
        payload.put("originalPartition", record.partition());
        payload.put("originalOffset", record.offset());
        payload.put("key", record.key());
        payload.put("value", record.value());
        payload.put("deliveryAttempt", readDeliveryAttempt(record));
        payload.put("errorType", ex.getClass().getSimpleName());
        payload.put("errorMessage", ex.getMessage());
        payload.put("failedAt", Instant.now().toString());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException jsonException) {
            return "{\"error\":\"dlq payload serialization failed\"}";
        }
    }

    private Integer readDeliveryAttempt(ConsumerRecord<?, ?> record) {
        if (record.headers() == null || record.headers().lastHeader(KafkaHeaders.DELIVERY_ATTEMPT) == null) {
            return null;
        }
        byte[] value = record.headers().lastHeader(KafkaHeaders.DELIVERY_ATTEMPT).value();
        if (value == null || value.length < Integer.BYTES) {
            return null;
        }
        return ByteBuffer.wrap(value).getInt();
    }
}
