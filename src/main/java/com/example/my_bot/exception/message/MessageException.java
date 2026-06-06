package com.example.my_bot.exception.message;

public abstract class MessageException extends RuntimeException {
    public MessageException(String message) {
        super(message);
    }
}