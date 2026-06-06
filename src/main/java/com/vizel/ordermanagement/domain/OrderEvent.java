package com.vizel.ordermanagement.domain;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "order_events")
@Data
public class OrderEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String aggregateId;

    private String eventType;

    @Column(columnDefinition = "JSON")
    private String payload;

    @Enumerated(EnumType.STRING)
    private EventStatus status = EventStatus.PENDING;

    private LocalDateTime createdAt = LocalDateTime.now();

    public enum EventStatus {
        PENDING, PROCESSED
    }
}
