package com.example.my_bot.exception.permission;

public abstract class PermissionException extends RuntimeException {
    public PermissionException(String message) {
        super(message);
    }
}