package com.example.my_bot.exception.role;

import lombok.NonNull;

public class DuplicateRoleNameException extends RoleException {


    public DuplicateRoleNameException(@NonNull String name) {
        super("Роль с названием «%s» уже существует.".formatted(name));
    }
}
