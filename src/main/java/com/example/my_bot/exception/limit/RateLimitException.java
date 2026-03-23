package com.example.my_bot.exception.limit;

public abstract class RateLimitException extends RuntimeException {
    public RateLimitException(String message) {
        super(message);
    }
}