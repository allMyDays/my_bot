package com.example.my_bot.exception.timer;

import lombok.NonNull;

public class TimerDateOutOfBoundsException extends TimerException{


    public TimerDateOutOfBoundsException(@NonNull String message) {

        super(message);
    }
}
