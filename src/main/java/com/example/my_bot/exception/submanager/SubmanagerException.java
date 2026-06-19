package com.example.my_bot.exception.submanager;

public abstract class SubmanagerException extends RuntimeException {
    public SubmanagerException(String message) {
        super(message);
    }
}