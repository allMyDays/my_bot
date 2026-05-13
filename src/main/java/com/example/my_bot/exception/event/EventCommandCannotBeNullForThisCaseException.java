package com.example.my_bot.exception.event;

import lombok.NonNull;

public class EventCommandCannotBeNullForThisCaseException extends EventException {


    public EventCommandCannotBeNullForThisCaseException(@NonNull String message) {
        super(message);
    }
}
