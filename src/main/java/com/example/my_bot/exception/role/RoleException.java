package com.example.my_bot.exception.role;

public abstract class RoleException extends RuntimeException {
    public RoleException(String message) {
        super(message);
    }
}