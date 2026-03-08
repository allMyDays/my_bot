package com.example.my_bot.exception.role;

import lombok.NonNull;

public class DuplicateRoleNameException extends RoleException {


    public DuplicateRoleNameException(String exceptionName) {
        super(exceptionName);
    }
}
