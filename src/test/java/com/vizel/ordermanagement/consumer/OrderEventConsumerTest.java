package com.vizel.ordermanagement.consumer;

import com.vizel.ordermanagement.domain.Order;
import com.vizel.ordermanagement.domain.ProcessedMessage;
import com.vizel.ordermanagement.repository.OrderRepository;
import com.vizel.ordermanagement.repository.ProcessedMessageRepository;
import com.vizel.ordermanagement.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderEventConsumerTest {

    @Mock
    private ProcessedMessageRepository processedMessageRepository;
    @Mock
    private PaymentService paymentService;
    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderEventConsumer consumer;

    @Test
    void handleOrderCreatedEvent_Success_ProcessesPaymentAndUpdatesStatus() {
        String aggregateId = "ORDER-123";
        String payload = "{\"id\":\"ORDER-123\",\"customerId\":\"CUST-1\"}";

        when(processedMessageRepository.existsById("PAYMENT_FOR_ORDER_" + aggregateId)).thenReturn(false);

        Order order = new Order();
        order.setId(aggregateId);
        order.setStatus(Order.OrderStatus.PENDING);
        when(orderRepository.findById(aggregateId)).thenReturn(Optional.of(order));

        consumer.handleOrderCreatedEvent(payload, aggregateId);

        verify(paymentService).chargeCustomer(payload);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertEquals(Order.OrderStatus.COMPLETED, orderCaptor.getValue().getStatus());

        ArgumentCaptor<ProcessedMessage> messageCaptor = ArgumentCaptor.forClass(ProcessedMessage.class);
        verify(processedMessageRepository).save(messageCaptor.capture());
        assertEquals("PAYMENT_FOR_ORDER_ORDER-123", messageCaptor.getValue().getMessageId());
        assertNotNull(messageCaptor.getValue().getProcessedAt());
    }

    @Test
    void handleOrderCreatedEvent_DuplicateMessage_SkipsProcessing() {
        String aggregateId = "ORDER-123";
        String payload = "{\"id\":\"ORDER-123\"}";

        when(processedMessageRepository.existsById("PAYMENT_FOR_ORDER_" + aggregateId)).thenReturn(true);

        consumer.handleOrderCreatedEvent(payload, aggregateId);

        verify(paymentService, never()).chargeCustomer(any());
        verify(orderRepository, never()).findById(any());
        verify(orderRepository, never()).save(any());
        verify(processedMessageRepository, never()).save(any());
    }

    @Test
    void handleOrderCreatedEvent_OrderNotFound_ThrowsIllegalStateException() {
        String aggregateId = "MISSING-ORDER";
        String payload = "{}";

        when(processedMessageRepository.existsById("PAYMENT_FOR_ORDER_" + aggregateId)).thenReturn(false);
        when(orderRepository.findById(aggregateId)).thenReturn(Optional.empty());

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> consumer.handleOrderCreatedEvent(payload, aggregateId));

        assertTrue(exception.getMessage().contains("MISSING-ORDER"));

        verify(paymentService).chargeCustomer(payload);
        verify(processedMessageRepository, never()).save(any());
    }
}
