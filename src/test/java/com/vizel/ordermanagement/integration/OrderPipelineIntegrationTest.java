package com.vizel.ordermanagement.integration;

import com.vizel.ordermanagement.domain.Inventory;
import com.vizel.ordermanagement.domain.Order;
import com.vizel.ordermanagement.domain.OrderAuditDocument;
import com.vizel.ordermanagement.dto.CreateOrderRequest;
import com.vizel.ordermanagement.repository.*;
import com.vizel.ordermanagement.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class OrderPipelineIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProcessedMessageRepository processedMessageRepository;

    @Autowired
    private OrderAuditLogRepository mongoAuditRepository;

    @Test
    void testFullAsyncPipeline_FromOrderCreationToMongoAudit() {

        String sku = "PIPELINE-SKU";
        inventoryRepository.deleteById(sku);
        Inventory inventory = new Inventory(sku, 5);
        inventoryRepository.save(inventory);

        CreateOrderRequest request = new CreateOrderRequest();
        request.setCustomerId("CUST-PIPELINE");
        request.setItems(Map.of(sku, 2));

        mongoAuditRepository.deleteAll();

        orderService.createOrder(request);

        Inventory updatedInventory = inventoryRepository.findById(sku).orElseThrow();
        assertEquals(3, updatedInventory.getQuantity());

        await().atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    List<Order> orders = orderRepository.findAll();
                    Order order = orders.stream()
                            .filter(o -> "CUST-PIPELINE".equals(o.getCustomerId()))
                            .findFirst()
                            .orElse(null);
                    assertTrue(order != null, "Order should be persisted");
                    assertEquals(Order.OrderStatus.COMPLETED, order.getStatus(),
                            "Order status must be updated to COMPLETED");

                    String idempotencyKey = "PAYMENT_FOR_ORDER_" + order.getId();
                    assertTrue(processedMessageRepository.existsById(idempotencyKey),
                            "Idempotency key should be created");

                    List<OrderAuditDocument> audits = mongoAuditRepository.findAll();
                    boolean auditExists = audits.stream()
                            .anyMatch(doc -> doc.getEventPayload() != null
                                    && doc.getEventPayload().contains(order.getId()));
                    assertTrue(auditExists, "Audit log document must be present in MongoDB");
                });
    }
}
