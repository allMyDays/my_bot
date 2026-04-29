package com.example.my_bot.exception.event;

import com.example.my_bot.exception.timer.TimerException;
import lombok.NonNull;

public class TooManyEventsException extends EventException{

    public TooManyEventsException() {

        super("Превышен лимит создания новых событий. Удалите существующее событие, чтобы добавить новое.");
    }
    public TooManyEventsException(@NonNull String message) {

        super(message);
    }
}
