package com.example.my_bot.exception.timer;

import lombok.NonNull;

public class IncorrectTimerExecutionLimitException extends TimerException {


    public IncorrectTimerExecutionLimitException(@NonNull String message) {
        super(message);
    }
}
