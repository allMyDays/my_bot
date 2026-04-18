package com.example.my_bot.exception.event;

public abstract class EventException extends RuntimeException {
    public EventException(String message) {
        super(message);
    }
}