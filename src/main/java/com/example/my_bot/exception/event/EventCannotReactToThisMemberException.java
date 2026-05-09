package com.example.my_bot.exception.event;

import lombok.NonNull;

public class EventCannotReactToThisMemberException extends EventException{


    public EventCannotReactToThisMemberException(@NonNull String message) {
        super(message);
    }
}
