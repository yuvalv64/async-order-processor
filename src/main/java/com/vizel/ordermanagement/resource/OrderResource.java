package com.vizel.ordermanagement.resource;

import com.vizel.ordermanagement.api.OrdersApi;
import com.vizel.ordermanagement.controller.OrderController;
import com.vizel.ordermanagement.dto.CreateOrderResponse;
import com.vizel.ordermanagement.dto.CreateOrderRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class OrderResource implements OrdersApi {

    private final OrderController orderController;

    @Override
    public ResponseEntity<CreateOrderResponse> createOrder(CreateOrderRequest orderRequest) {
        return orderController.createOrder(orderRequest);
    }
}
