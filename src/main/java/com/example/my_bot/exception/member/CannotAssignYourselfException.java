package com.example.my_bot.exception.member;

import com.example.my_bot.exception.role.RoleException;

import static com.example.my_bot.utils.VkChatUtils.createMention;

public class CannotAssignYourselfException extends MemberException {
    public CannotAssignYourselfException() {
        super("Нельзя изменить роль самому себе.");
    }
}
