package com.example.my_bot.exception.command;

import lombok.NonNull;

import static com.example.my_bot.utils.TextUtils.createMention;

public class CommandAccessDeniedException extends CommandException {
    public CommandAccessDeniedException(long userId, @NonNull String command) {
        super("%s(Вы) не можете взаимодействовать с командой «%s».".formatted(createMention(userId),command));
    }
}
