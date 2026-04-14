package com.example.my_bot.exception.command;

public class CannotApplyThisCommandToChatAdminException extends CommandException {
    public CannotApplyThisCommandToChatAdminException() {
        super("Эту команду нельзя применить к участнику, у которого есть «админка» в самом чате.");
    }
}
