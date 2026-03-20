package com.example.my_bot.exception.member;

import static com.example.my_bot.utils.ChatUtils.createMention;

public class MemberAccessDeniedException extends MemberException {
    public MemberAccessDeniedException(long userToAssign, long fromId) {
        super("%s(Вашей) роли недостаточно для того, чтобы изменить любые параметры %s(этого участника)."
                .formatted(createMention(fromId), createMention(userToAssign)));
    }
}
