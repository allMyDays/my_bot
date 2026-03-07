package com.example.my_bot.exception.role;

public class RoleNameLengthOutOfBoundsException extends RoleException  {

    public RoleNameLengthOutOfBoundsException(int min, int max) {
        super("Название роли не может быть короче чем %d или длиннее чем %d символов.".formatted(min, max));
    }
}
