package com.example.my_bot.exception.member;

import static com.example.my_bot.utils.ChatUtils.createMention;

public class UserNeverBeenInChatException extends MemberException {
    public UserNeverBeenInChatException(long userId) {
        super(createMention(userId)+"(Данный пользователь) не является текущим или бывшим участником чата.");
    }
}
