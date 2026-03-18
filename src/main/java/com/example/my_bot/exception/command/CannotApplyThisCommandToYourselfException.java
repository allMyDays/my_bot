package com.example.my_bot.exception.command;

public class CannotApplyThisCommandToYourselfException extends CommandException {
    public CannotApplyThisCommandToYourselfException() {
        super("Эту команду нельзя применить к самому себе.");
    }
}
