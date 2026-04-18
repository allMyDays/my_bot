package com.example.my_bot.exception.event;

import com.example.my_bot.enumeration.event.MyEventType;
import lombok.NonNull;

public class EventTypeNotRequiresArgumentException extends EventException {


    public EventTypeNotRequiresArgumentException(@NonNull MyEventType myEventType) {
        super("Событие «%s» не требует никакой аргумент.".formatted(myEventType.getCyrillicType()));
    }
}
