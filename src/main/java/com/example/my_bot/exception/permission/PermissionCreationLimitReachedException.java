package com.example.my_bot.exception.permission;

import com.example.my_bot.exception.role.RoleException;

public class PermissionCreationLimitReachedException extends PermissionException {


    public PermissionCreationLimitReachedException() {

        super("Был достигнут лимит создания настроек прав для команд. Удалите существующие, чтобы добавить новые.");
    }
}
