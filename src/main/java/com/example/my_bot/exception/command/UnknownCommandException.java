package com.example.my_bot.exception.command;

import lombok.NonNull;

public class UnknownCommandException extends CommandException {
    public UnknownCommandException(@NonNull String command) {
        super("Следующая команда является неизвестной: "+command);
    }
}
