package com.example.my_bot.exception.user;

public abstract class GlobalUserException extends RuntimeException {
    public GlobalUserException(String message) {
        super(message);
    }
}