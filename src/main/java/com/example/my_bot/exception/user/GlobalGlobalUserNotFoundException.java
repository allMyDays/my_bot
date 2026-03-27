package com.example.my_bot.exception.user;

public class GlobalGlobalUserNotFoundException extends GlobalUserException {
    public GlobalGlobalUserNotFoundException(long userId) {
        super("пользователь с id %d не найден в базе данных, вероятнее всего, он не является лицом, когда-либо писавшим сообщения в чатах с ботом."
                .formatted(userId));
    }
}
