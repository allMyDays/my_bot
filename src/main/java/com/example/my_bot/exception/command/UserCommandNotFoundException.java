package com.example.my_bot.exception.command;

import lombok.NonNull;

public class UserCommandNotFoundException extends CommandException{

    public UserCommandNotFoundException(@NonNull String userCommand) {
        super("Указанный вами аргумент «%s» не является действующей командой.".formatted(userCommand));
    }
}
