package com.example.my_bot.exception.event;

import lombok.NonNull;

public class CannotApplyThisFunctionToPersonalEventException extends EventException {


    public CannotApplyThisFunctionToPersonalEventException() {
        super("Данную функцию нельзя применить к персональному событию (событию, которое реагирует только на конкретного участника).");
    }
}
