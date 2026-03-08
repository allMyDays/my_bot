package com.example.my_bot.enumeration;

import lombok.NonNull;

import java.util.Optional;

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

    public static Optional<String> getRoleNameByPriority(int priority) {
        for (DefaultRole role : DefaultRole.values()) {
            if (role.getRolePriority() == priority) {
                return Optional.of(role.getRoleName());
            }
        }
        return Optional.empty();
    }

    public static Optional<DefaultRole> getRoleByName(@NonNull String name) {
         name=name.trim();
        for (DefaultRole role : DefaultRole.values()) {
            if (role.getRoleName().equalsIgnoreCase(name)) {
                return Optional.of(role);
            }
        }
        return Optional.empty();
    }



    public static boolean isDefaultRole(int priority) {
        for (DefaultRole role : DefaultRole.values()) {
            if (role.getRolePriority() == priority) {
                return true;
            }
        }
        return false;

    }
    public static boolean isDefaultRole(@NonNull String name) {
           name=name.trim();
        for (DefaultRole role : DefaultRole.values()) {
            if (role.getRoleName().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;

    }
}
