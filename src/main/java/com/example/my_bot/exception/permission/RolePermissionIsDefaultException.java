package com.example.my_bot.exception.permission;

public class RolePermissionIsDefaultException extends PermissionException{

    public RolePermissionIsDefaultException() {
        super("Данная роль является дефолтной для этой команды, для возврата к дефолту нужно удалить настройку, а не создавать новую.");
    }
}
