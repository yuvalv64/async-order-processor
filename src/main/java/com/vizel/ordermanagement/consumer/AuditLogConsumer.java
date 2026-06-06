package com.vizel.ordermanagement.consumer;

import com.vizel.ordermanagement.constant.KafkaConstants;
import com.vizel.ordermanagement.domain.OrderAuditDocument;
import com.vizel.ordermanagement.repository.OrderAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuditLogConsumer {

    private final OrderAuditLogRepository mongoAuditRepository;

    @KafkaListener(topics = KafkaConstants.ORDERS_TOPIC, groupId = KafkaConstants.AUDIT_LOG_GROUP)
    public void consumeAndLogEvent(String payload) {
        log.info("Saving event to MongoDB Audit Log...");

        OrderAuditDocument document = new OrderAuditDocument();
        document.setEventPayload(payload);
        document.setRecordedAt(LocalDateTime.now());

        mongoAuditRepository.save(document);
    }
}