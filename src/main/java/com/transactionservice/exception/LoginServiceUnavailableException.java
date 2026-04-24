package com.transactionservice.exception;

public class LoginServiceUnavailableException extends RuntimeException {

    public LoginServiceUnavailableException(String message) {
        super(message);
    }

    public LoginServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
