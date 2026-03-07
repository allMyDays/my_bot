package com.example.my_bot.exception.role;

public class RolePriorityOutOfBoundsException extends RoleException {

    public RolePriorityOutOfBoundsException(int min, int max) {
        super("Приоритет роли нельзя указать ниже чем %d или выше чем %d.".formatted(min, max));
    }
}
