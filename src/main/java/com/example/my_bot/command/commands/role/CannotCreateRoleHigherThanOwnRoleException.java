package com.example.my_bot.command.commands.role;

import com.example.my_bot.exception.role.RoleException;

public class CannotCreateRoleHigherThanOwnRoleException extends RoleException {
    public CannotCreateRoleHigherThanOwnRoleException() {
        super("Вы пытаетесь создать роль с приоритетом выше, чем у Вашей собственной роли.");
    }
}
