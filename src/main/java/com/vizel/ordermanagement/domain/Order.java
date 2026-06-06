package com.vizel.ordermanagement.domain;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
@Data
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String customerId;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    @Column(columnDefinition = "JSON")
    private String itemsJson;

    private LocalDateTime createdAt = LocalDateTime.now();

    public enum OrderStatus {
        PENDING, ACCEPTED, REJECTED, COMPLETED, FAILED
    }
}