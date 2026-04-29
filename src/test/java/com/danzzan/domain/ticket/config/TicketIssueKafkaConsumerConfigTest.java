package com.danzzan.domain.ticket.config;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TicketIssueKafkaConsumerConfigTest {

    @Test
    void listenerFactory_설정한_concurrency를_반영한다() {
        TicketIssueKafkaConsumerConfig config = new TicketIssueKafkaConsumerConfig();
        ConsumerFactory<String, String> consumerFactory = mock(ConsumerFactory.class);
        DefaultErrorHandler errorHandler = mock(DefaultErrorHandler.class);

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                config.ticketIssueKafkaListenerContainerFactory(consumerFactory, errorHandler, 3);

        assertThat(ReflectionTestUtils.getField(factory, "concurrency")).isEqualTo(3);
        assertThat(factory.getContainerProperties().getAckMode()).isEqualTo(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
    }
}
