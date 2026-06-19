package com.example.my_bot.exception.member;

import lombok.NonNull;

import java.util.List;
import java.util.stream.Collectors;

import static com.example.my_bot.utils.TextUtils.createMention;

public class MemberNotFoundException extends MemberException {
    public MemberNotFoundException(long memberId, long chatId) {
        super("Ошибка: не найден участник %d в чате %d".formatted(memberId, chatId));
    }
}
