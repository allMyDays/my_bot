package com.example.my_bot.exception.event;

import lombok.NonNull;

public class ThisEventAlreadyHasSuchRoleException extends EventException {


    public ThisEventAlreadyHasSuchRoleException() {
        super("Данное событие уже имеет такую роль.");
    }
}
