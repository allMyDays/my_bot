package com.example.my_bot.exception.chat;


import lombok.NonNull;

public class LogChatException extends ChatException {

    public LogChatException(@NonNull String message) {
        super(message);


    }
}
