package com.example.my_bot.exception.event;

import com.example.my_bot.exception.timer.TimerException;
import lombok.NonNull;

public class CannotUseThisCommandForEventException extends EventException {


    public CannotUseThisCommandForEventException(@NonNull String command) {
        super("Команду «%s» нельзя использовать при создании события.".formatted(command));
    }
}
