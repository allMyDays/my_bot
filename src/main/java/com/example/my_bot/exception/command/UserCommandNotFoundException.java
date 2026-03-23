package com.example.my_bot.exception.command;

import lombok.NonNull;

import java.util.List;
import java.util.Set;

public class UserCommandNotFoundException extends CommandException{

    public UserCommandNotFoundException(@NonNull String userCommand) {
        super("Указанный вами аргумент «%s» не является действующей командой.".formatted(userCommand));
    }public UserCommandNotFoundException(@NonNull Set<String> userCommands) {
        super("Указанные вами аргументы не являются действующими командами: %s"
                .formatted(String.join(", ", userCommands)));
    }
}
