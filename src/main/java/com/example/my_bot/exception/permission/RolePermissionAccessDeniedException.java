package com.example.my_bot.exception.permission;

import com.example.my_bot.exception.role.RoleException;

import static com.example.my_bot.utils.VkChatUtils.createMention;

public class RolePermissionAccessDeniedException extends PermissionException {
    public RolePermissionAccessDeniedException(long userId) {
        super(createMention(userId)+"(Ваша) роль ниже, чем роль, которая сейчас настроена для доступа к этой команде.");
    }
}
