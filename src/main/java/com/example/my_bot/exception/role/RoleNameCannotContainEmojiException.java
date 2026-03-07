package com.example.my_bot.exception.role;

public class RoleNameCannotContainEmojiException extends RoleException{
    public RoleNameCannotContainEmojiException() {
        super("Название роли не может содержать эмоджи.");
    }
}
