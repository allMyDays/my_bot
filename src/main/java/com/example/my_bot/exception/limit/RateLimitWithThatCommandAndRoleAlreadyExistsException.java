package com.example.my_bot.exception.limit;

import lombok.NonNull;

public class RateLimitWithThatCommandAndRoleAlreadyExistsException extends RateLimitException {


    public RateLimitWithThatCommandAndRoleAlreadyExistsException(@NonNull String command, @NonNull String roleName) {

        super("Уже существует лимит на команду «%s» для роли «%s». Удалите этот лимит, если хотите его пересоздать с другими параметрами."
                .formatted(command, roleName));
    }
}
