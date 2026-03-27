package com.example.my_bot.exception.user;

import static com.example.my_bot.utils.ChatUtils.createMention;

public class GlobalGlobalUserDoesNotHaveRequiredBoundChatException extends GlobalUserException {
    public GlobalGlobalUserDoesNotHaveRequiredBoundChatException(long userId) {
        super("%s(Этот пользователь) не имеет привязку данного чата.".formatted(createMention(userId)));

    }
}
