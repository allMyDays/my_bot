package com.example.my_bot.exception.event;

public class CannotApplyThisCommandToPersonalEventException extends EventException {


    public CannotApplyThisCommandToPersonalEventException() {
        super("Данную функцию нельзя применить к личному событию.");
    }
}
