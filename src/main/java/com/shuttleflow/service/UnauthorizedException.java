package com.shuttleflow.service;

public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException() {
        this("Authentication is required.");
    }

    public UnauthorizedException(String message) {
        super(message);
    }
}
