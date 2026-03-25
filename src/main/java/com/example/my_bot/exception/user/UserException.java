package com.example.my_bot.exception.user;

public abstract class UserException extends RuntimeException {
    public UserException(String message) {
        super(message);
    }
}