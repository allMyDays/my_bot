package com.example.my_bot.exception.timer;

import lombok.NonNull;

public class CannotUseThisCommandForTimerException extends TimerException{


    public CannotUseThisCommandForTimerException(@NonNull String command) {
        super("Команду «%s» нельзя использовать в таймере.".formatted(command));
    }
}
