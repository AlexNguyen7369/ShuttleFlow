package com.shuttleflow.service;

public class BookingConflictException extends RuntimeException {
    public BookingConflictException() {
        super("Court is already booked.");
    }

    public BookingConflictException(String message) {
        super(message);
    }
}
