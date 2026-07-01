package com.example.my_bot.exception.chat;


import org.checkerframework.checker.nullness.qual.NonNull;

public class ChatEntityNotFoundException extends ChatException{

    public ChatEntityNotFoundException(long chatId) {
        super("Cannot find chat entity with Id "+chatId);

    }

    public ChatEntityNotFoundException(@NonNull String chatCode) {
        super("Не удалось найти чат с кодом «%s»".formatted(chatCode));

    }
}
