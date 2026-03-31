package com.example.my_bot.exception.timer;

import lombok.NonNull;

public class TimerIntervalOutOfBoundsException extends TimerException{


    public TimerIntervalOutOfBoundsException(@NonNull String message) {

        super(message);
    }
}
