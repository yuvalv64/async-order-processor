package com.vizel.ordermanagement.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import com.vizel.ordermanagement.dto.CreateOrderRequest;
import com.vizel.ordermanagement.domain.Inventory;
import com.vizel.ordermanagement.domain.Order;
import com.vizel.ordermanagement.domain.OrderEvent;
import com.vizel.ordermanagement.repository.InventoryRepository;
import com.vizel.ordermanagement.repository.OrderRepository;
import com.vizel.ordermanagement.repository.OrderEventRepository;
import com.vizel.ordermanagement.exception.LackOfQuantityException;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final InventoryRepository inventoryRepository;
    private final OrderEventRepository orderEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void createOrder(CreateOrderRequest request) {
        log.info("Starting order creation process for customer: {}", request.getCustomerId());

        for (Map.Entry<String, Integer> entry : request.getItems().entrySet()) {
            String sku = entry.getKey();
            int requestedQuantity = entry.getValue();

            Inventory inventory = inventoryRepository.findById(sku)
                    .orElseThrow(() -> new IllegalArgumentException("SKU not found: " + sku));

            if (inventory.getQuantity() < requestedQuantity) {
                throw new LackOfQuantityException("Lack of quantity for SKU: " + sku);
            }

            inventory.setQuantity(inventory.getQuantity() - requestedQuantity);

            try {
                inventoryRepository.saveAndFlush(inventory);
            } catch (ObjectOptimisticLockingFailureException e) {
                log.error("Optimistic lock failure for SKU: {} during order creation", sku);
                throw new LackOfQuantityException("The item is being purchased by another user. Please try again.");
            }
        }

        Order order = new Order();
        order.setCustomerId(request.getCustomerId());
        order.setStatus(Order.OrderStatus.PENDING);
        try {
            order.setItemsJson(objectMapper.writeValueAsString(request.getItems()));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize order items to JSON", e);
        }

        order = orderRepository.save(order);

        OrderEvent orderEvent = new OrderEvent();
        orderEvent.setAggregateId(order.getId());
        orderEvent.setEventType("OrderCreated");
        try {
            orderEvent.setPayload(objectMapper.writeValueAsString(order));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize order event payload to JSON", e);
        }

        orderEventRepository.save(orderEvent);
        log.info("Order successfully persisted with ID: {} and registered in OrderEvent", order.getId());

    }
}