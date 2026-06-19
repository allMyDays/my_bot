package com.example.my_bot.vk.enumeration;

import lombok.Getter;
import lombok.NonNull;

import java.util.Set;

public enum CommunityErrorCode {

    NO_GROUP_MEMBERS_ACCESS(Set.of(15,203)),

    CALLBACK_SERVER_IS_NOT_FOUND(Set.of(104));

    @Getter
    private final Set<Integer> codes;


    CommunityErrorCode(@NonNull Set<Integer> codes) {
        this.codes = codes;
    }
}
