package com.example.my_bot.exception.member;

import static com.example.my_bot.utils.ChatUtils.createMention;

public class MemberAlreadyHasThisRoleException extends MemberException {
    public MemberAlreadyHasThisRoleException(long userId) {

        super(createMention(userId)+"(Пользователь) уже имеет данную роль.");
    }
}
