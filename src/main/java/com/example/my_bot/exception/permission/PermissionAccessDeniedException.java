package com.example.my_bot.exception.permission;

import static com.example.my_bot.utils.VkChatUtils.createMention;

public class PermissionAccessDeniedException extends PermissionException {
    public PermissionAccessDeniedException(long userId) {
        super(createMention(userId)+"(Ваша) роль ниже, чем роль, которая сейчас настроена для доступа к этой команде.");
    }
}
