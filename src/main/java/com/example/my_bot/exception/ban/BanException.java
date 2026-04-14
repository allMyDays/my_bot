package com.example.my_bot.exception.ban;

public abstract class BanException extends RuntimeException {
    public BanException(String message) {
        super(message);
    }
}