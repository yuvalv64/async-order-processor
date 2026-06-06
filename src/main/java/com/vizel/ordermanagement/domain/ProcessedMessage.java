package com.vizel.ordermanagement.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "processed_messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProcessedMessage {

    @Id
    private String messageId;

    private LocalDateTime processedAt;
}