package com.vizel.ordermanagement.constant;

public final class KafkaConstants {
    private KafkaConstants() {}

    public static final String ORDERS_TOPIC = "orders-topic";
    public static final String AUDIT_LOG_GROUP = "audit-log-group";
    public static final String ORDER_PAYMENT_GROUP = "order-payment-group";
}
