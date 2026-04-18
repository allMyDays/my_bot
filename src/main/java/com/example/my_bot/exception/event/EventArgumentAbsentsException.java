package com.example.my_bot.exception.event;

import com.example.my_bot.enumeration.event.MyEventType;
import lombok.NonNull;

public class EventArgumentAbsentsException extends EventException {


    public EventArgumentAbsentsException(MyEventType eventType) {
        super("Событие «%s» требует обязательный аргумент, который не был указан.".formatted(eventType.getCyrillicType()));
    }
}
