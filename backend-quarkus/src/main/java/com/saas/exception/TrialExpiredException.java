package com.saas.exception;

public class TrialExpiredException extends RuntimeException {
    public TrialExpiredException(String message) {
        super(message);
    }
}
