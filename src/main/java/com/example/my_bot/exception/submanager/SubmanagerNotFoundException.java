package com.example.my_bot.exception.submanager;

import com.example.my_bot.exception.member.MemberException;

import static com.example.my_bot.utils.TextUtils.createMention;

public class SubmanagerNotFoundException extends SubmanagerException {
    public SubmanagerNotFoundException(long groupId) {
        super("Субменеджер с group id %d не был найден в базе данных!".formatted(groupId));
    }
}
