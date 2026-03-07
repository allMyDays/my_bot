package com.example.my_bot.exception.role;

public class RolePriorityAccessDeniedException extends RoleException {
    public RolePriorityAccessDeniedException() {
        super("Нельзя создать/удалить/отредактировать роль с приоритетом выше, чем у Вашей роли.");
    }
}
