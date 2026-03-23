package com.example.my_bot.exception.limit;

public abstract class LimitException extends RuntimeException {
    public LimitException(String message) {
        super(message);
    }
}