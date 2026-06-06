package com.vizel.ordermanagement.producer;

import com.vizel.ordermanagement.constant.KafkaConstants;
import com.vizel.ordermanagement.domain.OrderEvent;
import com.vizel.ordermanagement.repository.OrderEventRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventWorker {

    private final OrderEventRepository orderEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelayString = "${order-event.relay.fixed-delay:2000}")
    public void processOrderEventMessages() {

        List<OrderEvent> pendingEvents = orderEventRepository.findByStatus(OrderEvent.EventStatus.PENDING);

        if (!pendingEvents.isEmpty()) {
            log.info("Found {} pending order events. Starting relay to Kafka.", pendingEvents.size());
        }

        for (OrderEvent event : pendingEvents) {
            String key = event.getAggregateId();
            String payload = event.getPayload();
            if (key == null || payload == null) {
                log.error("Invalid OrderEvent {}: aggregateId or payload is null. Skipping.", event.getId());
                continue;
            }
            try {
                kafkaTemplate.send(KafkaConstants.ORDERS_TOPIC, key, payload).get();
                // Waiting for confirmation from Kafka
                event.setStatus(OrderEvent.EventStatus.PROCESSED);
                orderEventRepository.save(event);

                log.debug("Successfully relayed order event {} for order {}", event.getId(), key);

            } catch (Exception e) {
                log.error("Failed to send order event {} to Kafka. Will retry next cycle.", event.getId(), e);
            }
        }
    }
}
