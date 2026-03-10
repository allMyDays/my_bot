package com.example.my_bot.exception.chat;


public class ChatEntityNotFoundException extends RuntimeException{

    public ChatEntityNotFoundException(long chatId) {
        super("Cannot find chat entity with Id "+chatId);


    }
}
