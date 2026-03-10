package com.example.my_bot.exception.command;

public abstract class CommandException extends RuntimeException {
    public CommandException(String message) {
        super(message);
    }
}