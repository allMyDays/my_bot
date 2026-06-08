package com.example.my_bot.exception.member;

import static com.example.my_bot.utils.TextUtils.createMention;

public class MemberHasNoImmunityException extends MemberException {
    public MemberHasNoImmunityException(long userId) {

        super(createMention(userId)+"(Пользователь) не имеет какой-либо иммунитет от ролей.");
    }
}
