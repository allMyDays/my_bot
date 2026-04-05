package com.example.my_bot.exception.command;

import lombok.NonNull;

public class ForbiddenCommandForCurrentModeException extends CommandException {
    public ForbiddenCommandForCurrentModeException(@NonNull String command) {
        super("Команда «%s» является запрещённой для текущего мода.".formatted(command));
    }
}
