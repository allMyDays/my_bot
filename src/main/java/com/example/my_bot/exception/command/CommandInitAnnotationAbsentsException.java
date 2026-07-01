package com.example.my_bot.exception.command;

import lombok.NonNull;

public class CommandInitAnnotationAbsentsException extends CommandException{

    public CommandInitAnnotationAbsentsException(@NonNull String command) {
        super("Cannot find required init-annotation @Command for "+command);
    }
}
