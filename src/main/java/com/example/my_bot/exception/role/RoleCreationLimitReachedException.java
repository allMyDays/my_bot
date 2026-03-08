package com.example.my_bot.exception.role;

public class RoleCreationLimitReachedException extends RoleException {


    public RoleCreationLimitReachedException() {

        super("Был достигнут лимит создания ролей. Удалите существующую роль, чтобы добавить новую.");
    }
}
