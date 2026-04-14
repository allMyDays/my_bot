package com.example.my_bot.exception.ban;

import static com.example.my_bot.utils.ChatUtils.createMention;

public class UserHasNotBannedException extends BanException {

    public UserHasNotBannedException(long userId) {
        super(createMention(userId)+"(Данный пользователь) не находится в бане.");
    }
}
