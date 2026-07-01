package com.example.my_bot.exception.chat;


import lombok.NonNull;

public class AdminChatException extends ChatException {

    public AdminChatException(@NonNull String message) {
        super(message);


    }
}
