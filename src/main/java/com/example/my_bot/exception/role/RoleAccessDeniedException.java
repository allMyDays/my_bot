package com.example.my_bot.exception.role;

public class RoleAccessDeniedException extends RoleException {
    public RoleAccessDeniedException() {
        super("У вас недостаточно прав для того, чтобы работать с настолько высокой ролью.");
    }
}
