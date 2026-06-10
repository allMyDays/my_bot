package com.example.my_bot.vk.enumeration;

import lombok.Getter;

public enum WriteRestrictionAction {
    READ_ONLY("ro"), READ_AND_WRITE("rw");

    @Getter
    private final String vkType;

    WriteRestrictionAction(String vkType) {
        this.vkType = vkType;
    }
}
