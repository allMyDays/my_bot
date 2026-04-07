package com.example.my_bot.exception.timer;

public class TimerAlreadyHasThatNextExecutionException extends TimerException {


    public TimerAlreadyHasThatNextExecutionException() {
        super("Данный таймер уже имеет такое время следующего срабатывания.");
    }
}
