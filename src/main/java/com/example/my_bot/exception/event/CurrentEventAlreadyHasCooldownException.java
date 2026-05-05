package com.example.my_bot.exception.event;

public class CurrentEventAlreadyHasCooldownException extends EventException {


    public CurrentEventAlreadyHasCooldownException() {
        super("Данное событие уже имеет кулдаун.");
    }
}
