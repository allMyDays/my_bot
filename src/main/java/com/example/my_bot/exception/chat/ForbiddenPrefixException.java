package com.example.my_bot.exception.chat;

public class ForbiddenPrefixException extends ChatException{
    public ForbiddenPrefixException(char prefix) {
        super("Символ %c нельзя установить как префикс для команд.".formatted(prefix));
    }
}
