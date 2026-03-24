package com.example.my_bot.exception.user;

import com.example.my_bot.exception.member.MemberException;

import static com.example.my_bot.utils.ChatUtils.createMention;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(long userId) {
        super("пользователь с id %d не найден в базе данных, вероятнее всего, он не является лицом, когда-либо писавшим сообщения в чатах с ботом."
                .formatted(userId));
    }
}
