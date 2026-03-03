package com.example.my_bot.enumeration;

public enum DefaultRole {

    MEMBER("Участник", 0), MODERATOR("Модератор", 20), SENIOR_MODERATOR("Ст.модератор", 40),
    ADMINISTRATOR("Администратор", 60),  SENIOR_ADMINISTRATOR("Ст.администратор", 80),CHAT_CREATOR("Создатель чата", 100);

    private final String roleName;

    private final int rolePriority;


       DefaultRole(String roleName, int rolePriority) {
        this.roleName = roleName;
        this.rolePriority = rolePriority;

    }

    public String getRoleName() {
        return roleName;
    }

    public int getRolePriority() {
        return rolePriority;
    }
}
