package com.example.my_bot.exception.warn;

public abstract class WarnException extends RuntimeException {
    public WarnException(String s) {
        super(s);
    }
    public WarnException() {
    }
}