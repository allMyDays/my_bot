package com.example.my_bot.exception.limit;

import com.example.my_bot.exception.permission.PermissionException;
import lombok.NonNull;

public class LimitWithThatCommandAndRoleAlreadyExistsException extends LimitException {


    public LimitWithThatCommandAndRoleAlreadyExistsException(@NonNull String command, @NonNull String roleName) {

        super("Уже существует лимит на команду «%s» для роли «%s». Удалите этот лимит, если хотите его пересоздать с другими параметрами."
                .formatted(command, roleName));
    }
}
