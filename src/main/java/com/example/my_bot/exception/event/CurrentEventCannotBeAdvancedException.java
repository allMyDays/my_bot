package com.example.my_bot.exception.event;

public class CurrentEventCannotBeAdvancedException extends EventException {


    public CurrentEventCannotBeAdvancedException() {
        super("Данное событие не может быть расширенным событием.");
    }
}
