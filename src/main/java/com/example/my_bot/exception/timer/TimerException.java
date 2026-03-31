package com.example.my_bot.exception.timer;

public abstract class TimerException extends RuntimeException {
    public TimerException(String message) {
        super(message);
    }
}