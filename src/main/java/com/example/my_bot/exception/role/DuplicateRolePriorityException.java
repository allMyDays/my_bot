package com.example.my_bot.exception.role;

public class DuplicateRolePriorityException extends RoleException {

    public DuplicateRolePriorityException(int priority) {

        super("Роль с приоритетом %d уже существует.".formatted(priority));
    }
}
