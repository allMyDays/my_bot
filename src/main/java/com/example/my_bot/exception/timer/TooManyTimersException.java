package com.example.my_bot.exception.timer;

import lombok.NonNull;

public class TooManyTimersException extends TimerException{

    public TooManyTimersException() {

        super("Превышен лимит создания новых таймеров. Удалите существующий, чтобы добавить новый.");
    }
}
