package com.example.my_bot.exception.event;

import com.example.my_bot.enumeration.event.MyEventType;

public class EventNotFoundException extends EventException {


    public EventNotFoundException(long id) {
        super("Не найдено события с id "+id);
    }
}
