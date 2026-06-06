package com.vizel.ordermanagement.service;

import com.vizel.ordermanagement.dto.CreateOrderRequest;
import com.vizel.ordermanagement.domain.Inventory;
import com.vizel.ordermanagement.exception.LackOfQuantityException;
import com.vizel.ordermanagement.repository.InventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class OrderConcurrencyTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private InventoryRepository inventoryRepository;

    private static final String SKU = "CONCURRENT-SKU";

    @BeforeEach
    void setup() {
        inventoryRepository.deleteAll();
        Inventory inventory = new Inventory(SKU, 10);
        inventoryRepository.save(inventory);
    }

    @Test
    void createOrder_ConcurrentRequests_OnlyOneSucceeds() throws InterruptedException {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setCustomerId("CUST-CONCURRENT");
        request.setItems(Map.of(SKU, 10));

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    orderService.createOrder(request);
                    successCount.incrementAndGet();
                } catch (LackOfQuantityException e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertEquals(1, successCount.get(), "Only one transaction should succeed");
        assertEquals(1, failCount.get(), "The other transaction must fail due to Optimistic Locking");

        Inventory finalInventory = inventoryRepository.findById(SKU).orElseThrow();
        assertEquals(0, finalInventory.getQuantity(), "Final inventory should be 0");
    }
}