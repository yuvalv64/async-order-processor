package com.vizel.ordermanagement.controller;

import com.vizel.ordermanagement.dto.CreateOrderResponse;
import com.vizel.ordermanagement.dto.CreateOrderRequest;
import com.vizel.ordermanagement.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    public ResponseEntity<CreateOrderResponse> createOrder(CreateOrderRequest orderRequest) {

        orderService.createOrder(orderRequest);

        CreateOrderResponse response = new CreateOrderResponse();
        response.setStatus("ACCEPTED");
        response.setMessage("Your order has been accepted and is being processed asynchronously.");

        return ResponseEntity.accepted().body(response);
    }
}
