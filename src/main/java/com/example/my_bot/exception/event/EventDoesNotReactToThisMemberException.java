package com.example.my_bot.exception.event;

import lombok.NonNull;

public class EventDoesNotReactToThisMemberException extends EventException{


    public EventDoesNotReactToThisMemberException(@NonNull String message) {
        super(message);
    }
}
