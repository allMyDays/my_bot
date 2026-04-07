package com.example.my_bot.exception.timer;

import com.example.my_bot.enumeration.timer.TimerType;

public class IllegalTimerTypeException extends TimerException {


    public IllegalTimerTypeException(TimerType illegalType) {
        super("В данной функции нельзя использовать таймер с типом «%s».".formatted(illegalType.getCyrillicType()));
    }
}
