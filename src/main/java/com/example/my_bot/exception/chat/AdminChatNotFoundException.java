package com.example.my_bot.exception.chat;


public class AdminChatNotFoundException extends AdminChatException {

    public AdminChatNotFoundException(long chatId) {
        super("Не удалось найти указанный админ-чат.");


    }
}
