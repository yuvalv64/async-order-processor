package com.vizel.ordermanagement.domain;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document(collection = "order_audit_logs")
public class OrderAuditDocument {

    @Id
    private String id;

    private String eventPayload;
    private LocalDateTime recordedAt;
}