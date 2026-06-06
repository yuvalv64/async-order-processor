package com.vizel.ordermanagement.exception;

public class LackOfQuantityException extends RuntimeException {
    public LackOfQuantityException(String message) {
        super(message);
    }
}
