package com.example.my_bot.exception.command;

import lombok.NonNull;

public class ThisCommandOnlyForConversationsException extends CommandException {
    public ThisCommandOnlyForConversationsException(@NonNull String command) {
        super("Команду «%s» можно использовать только в многопользовательских чатах.".formatted(command));
    }
}
