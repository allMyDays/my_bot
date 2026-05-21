package com.example.my_bot.exception.event;

public class CannotApplyThisFunctionToCommandEventException extends EventException {


    public CannotApplyThisFunctionToCommandEventException() {
        super("Данную функцию нельзя применить к событию, которое является событием-командой.");
    }
}
