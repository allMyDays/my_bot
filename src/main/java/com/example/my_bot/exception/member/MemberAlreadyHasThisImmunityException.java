package com.example.my_bot.exception.member;

import static com.example.my_bot.utils.TextUtils.createMention;

public class MemberAlreadyHasThisImmunityException extends MemberException {
    public MemberAlreadyHasThisImmunityException(long userId) {

        super(createMention(userId)+"(Пользователь) уже имеет иммунитет на указанную роль.");
    }
}
