package com.example.my_bot.exception.command;

import lombok.NonNull;

import java.util.Set;

public class CommandArgumentTooLongException extends CommandException{

    public CommandArgumentTooLongException(int maxSymbols) {
        super("Превышено максимальное количество символов для аргумента к команде.");
    }
}
