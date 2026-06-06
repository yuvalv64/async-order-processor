package com.vizel.ordermanagement.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class PaymentService {

    // Dummy service
    public void chargeCustomer(String payload) {
        log.info("Simulating payment process for payload: {}", payload);
    }
}
