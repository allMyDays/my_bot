package com.example.my_bot.exception.role;

public class RoleAccessDeniedException extends RoleException {
    public RoleAccessDeniedException() {
        super("Вы не можете создавать, а также удалять и изменять любые настройки ролей, чей приоритет выше чем у Вашей роли.");
    }
}
