package com.example.my_bot.exception.role;

import lombok.NonNull;

public class RoleNotFoundException extends RoleException {


    public RoleNotFoundException() {
        super("Не найдено роли с указанным названием или приоритетом.");
    }
}
