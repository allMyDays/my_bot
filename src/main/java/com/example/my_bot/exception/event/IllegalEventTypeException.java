package com.example.my_bot.exception.event;

import com.example.my_bot.enumeration.timer.TimerType;
import com.example.my_bot.exception.timer.TimerException;
import lombok.NonNull;

public class IllegalEventTypeException extends EventException {


    public IllegalEventTypeException(@NonNull String message) {
        super(message);
    }
}
