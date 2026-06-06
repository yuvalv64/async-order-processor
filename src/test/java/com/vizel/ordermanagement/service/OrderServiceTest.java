package com.vizel.ordermanagement.service;

import com.vizel.ordermanagement.dto.CreateOrderRequest;
import com.vizel.ordermanagement.domain.Inventory;
import com.vizel.ordermanagement.domain.Order;
import com.vizel.ordermanagement.exception.LackOfQuantityException;
import com.vizel.ordermanagement.repository.InventoryRepository;
import com.vizel.ordermanagement.repository.OrderRepository;
import com.vizel.ordermanagement.repository.OrderEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private InventoryRepository inventoryRepository;
    @Mock
    private OrderEventRepository orderEventRepository;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OrderService orderService;

    @Test
    void createOrder_Success_DeductsInventoryAndSavesOrder() throws Exception {
        String sku = "SKU-100";
        int initialQuantity = 10;
        int requestedQuantity = 3;

        CreateOrderRequest request = new CreateOrderRequest();
        request.setCustomerId("CUST-1");
        request.setItems(Map.of(sku, requestedQuantity));

        Inventory inventory = new Inventory(sku, initialQuantity);
        when(inventoryRepository.findById(sku)).thenReturn(Optional.of(inventory));

        Order savedOrder = new Order();
        savedOrder.setId("ORDER-ID-123");
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        orderService.createOrder(request);

        assertEquals(7, inventory.getQuantity(), "Validate that the inventory reduced by 3 items");
        verify(inventoryRepository).saveAndFlush(inventory);

        // Capture the Order that was saved and verify its fields
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());

        Order capturedOrder = orderCaptor.getValue();
        assertEquals("CUST-1", capturedOrder.getCustomerId());
        assertEquals(Order.OrderStatus.PENDING, capturedOrder.getStatus());

        verify(orderEventRepository).save(any());
    }


    @Test
    void createOrder_LackOfQuantity_ThrowsException() {
        String sku = "SKU-100";
        CreateOrderRequest request = new CreateOrderRequest();
        request.setCustomerId("CUST-1");
        request.setItems(Map.of(sku, 15));

        Inventory inventory = new Inventory(sku, 10);
        when(inventoryRepository.findById(sku)).thenReturn(Optional.of(inventory));

        LackOfQuantityException exception = assertThrows(
                LackOfQuantityException.class,
                () -> orderService.createOrder(request));

        assertTrue(exception.getMessage().contains("Lack of quantity"));
        verify(orderRepository, never()).save(any());
        verify(orderEventRepository, never()).save(any());
    }

    @Test
    void createOrder_SkuNotFound_ThrowsIllegalArgumentException() {
        String unknownSku = "SKU-UNKNOWN";
        CreateOrderRequest request = new CreateOrderRequest();
        request.setCustomerId("CUST-1");
        request.setItems(Map.of(unknownSku, 1));

        when(inventoryRepository.findById(unknownSku)).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> orderService.createOrder(request));

        assertTrue(exception.getMessage().contains(unknownSku));
        verify(inventoryRepository, never()).saveAndFlush(any());
        verify(orderRepository, never()).save(any());
        verify(orderEventRepository, never()).save(any());
    }

    @Test
    void createOrder_OptimisticLockConflict_ThrowsLackOfQuantityException() throws Exception {
        String sku = "SKU-100";
        CreateOrderRequest request = new CreateOrderRequest();
        request.setCustomerId("CUST-1");
        request.setItems(Map.of(sku, 2));

        Inventory inventory = new Inventory(sku, 10);
        when(inventoryRepository.findById(sku)).thenReturn(Optional.of(inventory));

        // Simulate another thread updated this SKU concurrently
        when(inventoryRepository.saveAndFlush(any()))
                .thenThrow(new ObjectOptimisticLockingFailureException(Inventory.class, sku));

        LackOfQuantityException exception = assertThrows(
                LackOfQuantityException.class,
                () -> orderService.createOrder(request));

        assertTrue(exception.getMessage().contains("being purchased by another user"));
        verify(orderRepository, never()).save(any());
        verify(orderEventRepository, never()).save(any());
    }

}
