package com.example.my_bot.exception;


import com.example.my_bot.exception.chat.ChatException;
import lombok.NonNull;

public class LogChatException extends ChatException {

    public LogChatException(@NonNull String message) {
        super(message);


    }
}
