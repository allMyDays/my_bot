package com.example.my_bot.exception.role;

public class CannotDeleteDefaultRoleException extends RoleException {

    public CannotDeleteDefaultRoleException() {

        super("Данная роль является системной, вы не можете её удалить.");
    }
}
