package com.example.my_bot.exception.event;

import com.example.my_bot.enumeration.timer.TimerType;
import com.example.my_bot.exception.timer.TimerException;
import lombok.NonNull;

public class IncorrectEventArgumentException extends EventException {


    public IncorrectEventArgumentException(@NonNull String message) {
        super(message);
    }
}
