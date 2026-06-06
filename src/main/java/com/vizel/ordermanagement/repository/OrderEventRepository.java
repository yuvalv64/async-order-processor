package com.vizel.ordermanagement.repository;

import com.vizel.ordermanagement.domain.OrderEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderEventRepository extends JpaRepository<OrderEvent, String> {
    List<OrderEvent> findByStatus(OrderEvent.EventStatus status);
}
