package com.example.my_bot.exception.timer;

import lombok.NonNull;

public class TimerHasReachedExecutionLimitException extends TimerException {


    public TimerHasReachedExecutionLimitException() {

        super("Данный таймер достиг лимита срабатывания.");
    }
}
