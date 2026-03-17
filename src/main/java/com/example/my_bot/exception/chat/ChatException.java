package com.example.my_bot.exception.chat;

public abstract class ChatException extends RuntimeException {
    public ChatException(String message) {
        super(message);
    }
}