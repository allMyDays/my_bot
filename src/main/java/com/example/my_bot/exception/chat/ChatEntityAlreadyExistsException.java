package com.example.my_bot.exception.chat;


public class ChatEntityAlreadyExistsException extends ChatException{

    public ChatEntityAlreadyExistsException(long chatId) {
        super("Chat entity with id %d already exists in data base ".formatted(chatId));


    }
}
