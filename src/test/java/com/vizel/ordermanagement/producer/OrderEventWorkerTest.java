package com.vizel.ordermanagement.producer;

import com.vizel.ordermanagement.domain.OrderEvent;
import com.vizel.ordermanagement.repository.OrderEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderEventWorkerTest {

    @Mock
    private OrderEventRepository orderEventRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks
    private OrderEventWorker orderEventWorker;

    @Test
    @SuppressWarnings("unchecked")
    void processOrderEventMessages_Success_RelaysToKafkaAndMarksProcessed() throws Exception {
        OrderEvent event = new OrderEvent();
        event.setId("EVENT-1");
        event.setAggregateId("ORDER-123");
        event.setPayload("{\"id\":\"ORDER-123\"}");
        event.setStatus(OrderEvent.EventStatus.PENDING);

        when(orderEventRepository.findByStatus(OrderEvent.EventStatus.PENDING))
                .thenReturn(List.of(event));

        CompletableFuture<SendResult<String, String>> future = CompletableFuture
                .completedFuture(mock(SendResult.class));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);

        orderEventWorker.processOrderEventMessages();

        verify(kafkaTemplate).send("orders-topic", "ORDER-123", "{\"id\":\"ORDER-123\"}");

        assertEquals(OrderEvent.EventStatus.PROCESSED, event.getStatus());
        verify(orderEventRepository).save(event);
    }

    @SuppressWarnings("null")
    @Test
    void processOrderEventMessages_KafkaSendFailure_Retries() throws Exception {
        OrderEvent event = new OrderEvent();
        event.setId("EVENT-1");
        event.setAggregateId("ORDER-123");
        event.setPayload("{\"id\":\"ORDER-123\"}");
        event.setStatus(OrderEvent.EventStatus.PENDING);

        when(orderEventRepository.findByStatus(OrderEvent.EventStatus.PENDING))
                .thenReturn(List.of(event));

        CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("Kafka Down"));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);

        orderEventWorker.processOrderEventMessages();

        verify(kafkaTemplate).send("orders-topic", "ORDER-123", "{\"id\":\"ORDER-123\"}");

        assertEquals(OrderEvent.EventStatus.PENDING, event.getStatus());
        verify(orderEventRepository, never()).save(any(OrderEvent.class));
    }

    @Test
    void processOrderEventMessages_NullFields_Skips() {
        OrderEvent invalidEvent = new OrderEvent();
        invalidEvent.setId("EVENT-ERR");
        invalidEvent.setAggregateId(null);
        invalidEvent.setPayload("payload");
        invalidEvent.setStatus(OrderEvent.EventStatus.PENDING);

        when(orderEventRepository.findByStatus(OrderEvent.EventStatus.PENDING))
                .thenReturn(List.of(invalidEvent));

        orderEventWorker.processOrderEventMessages();
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
        verify(orderEventRepository, never()).save(any());
    }

    @SuppressWarnings("null")
    @Test
    void processOrderEventMessages_EmptyList_DoesNothing() {
        when(orderEventRepository.findByStatus(OrderEvent.EventStatus.PENDING))
                .thenReturn(Collections.emptyList());

        orderEventWorker.processOrderEventMessages();

        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
        verify(orderEventRepository, never()).save(any());
    }
}
