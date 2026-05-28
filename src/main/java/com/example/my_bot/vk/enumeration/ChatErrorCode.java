package com.example.my_bot.vk.enumeration;

import lombok.Getter;
import lombok.NonNull;

import java.util.Set;

public enum ChatErrorCode {

    YOU_ARE_RESTRICTED_TO_WRITE(Set.of(983)),
    YOU_ARE_NOT_CHAT_ADMIN(Set.of(925)),
    YOU_LEFT_THIS_CHAT(Set.of(922)),
    CHAT_FORWARD_DISABLED(Set.of(994)),
    NO_CHAT_ACCESS(Set.of(917,15,7)),
    CURRENT_MESSAGE_CANNOT_BE_FORWARD(Set.of(969));



    @Getter
    private final Set<Integer> codes;

    ChatErrorCode(@NonNull Set<Integer> codes) {
        this.codes = codes;
    }
}
