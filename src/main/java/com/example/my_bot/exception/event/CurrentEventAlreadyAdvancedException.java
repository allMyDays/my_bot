package com.example.my_bot.exception.event;

public class CurrentEventAlreadyAdvancedException extends EventException {


    public CurrentEventAlreadyAdvancedException() {
        super("Данное событие уже является расширенным.");
    }
}
