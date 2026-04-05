package com.example.my_bot.exception.timer;

import com.example.my_bot.enumeration.timer.TimerType;

public class IllegalTimerTypeException extends TimerException {


    public IllegalTimerTypeException(TimerType illegalType) {
        super("Cannot use timer with type %s in current function".formatted(illegalType));
    }
}
