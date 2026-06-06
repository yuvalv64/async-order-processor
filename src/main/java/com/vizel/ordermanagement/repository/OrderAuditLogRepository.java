package com.vizel.ordermanagement.repository;

import com.vizel.ordermanagement.domain.OrderAuditDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderAuditLogRepository extends MongoRepository<OrderAuditDocument, String> {
}