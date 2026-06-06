package com.vizel.ordermanagement.consumer;

import com.vizel.ordermanagement.constant.KafkaConstants;
import com.vizel.ordermanagement.domain.Order;
import com.vizel.ordermanagement.repository.OrderRepository;
import com.vizel.ordermanagement.domain.ProcessedMessage;
import com.vizel.ordermanagement.repository.ProcessedMessageRepository;
import com.vizel.ordermanagement.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

    private final ProcessedMessageRepository processedMessageRepository;
    private final PaymentService paymentService;
    private final OrderRepository orderRepository;

    @KafkaListener(topics = KafkaConstants.ORDERS_TOPIC, groupId = KafkaConstants.ORDER_PAYMENT_GROUP)
    @SuppressWarnings("null")
    @Transactional
    public void handleOrderCreatedEvent(
            String payload,
            @Header(KafkaHeaders.RECEIVED_KEY) String aggregateId) {

        log.info("Received message from Kafka for Order ID (Aggregate): {}", aggregateId);

        String key = "PAYMENT_FOR_ORDER_" + aggregateId;

        if (processedMessageRepository.existsById(key)) {
            log.warn(
                    "Payment for Order {} was already processed.",
                    aggregateId);
            return;
        }

        log.info("Processing payment for Order ID: {}", aggregateId);
        paymentService.chargeCustomer(payload);

        Order order = orderRepository.findById(aggregateId)
                .orElseThrow(() -> new IllegalStateException("Order not found: " + aggregateId));
        order.setStatus(Order.OrderStatus.COMPLETED);
        orderRepository.save(order);

        ProcessedMessage processedMessage = new ProcessedMessage();
        processedMessage.setMessageId(key);
        processedMessage.setProcessedAt(LocalDateTime.now());

        processedMessageRepository.save(processedMessage);
        log.info("Payment successful and idempotency record saved for Order ID: {}", aggregateId);

    }
}