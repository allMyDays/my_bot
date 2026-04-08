package com.example.my_bot.exception.timer;

public class TimerAlreadyHasThatExecutionLimitException extends TimerException {


    public TimerAlreadyHasThatExecutionLimitException() {
        super("Данный таймер уже имеет такой лимит срабатывания.");
    }
}
