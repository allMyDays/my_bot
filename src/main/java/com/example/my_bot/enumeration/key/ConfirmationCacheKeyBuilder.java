package com.example.my_bot.enumeration.key;

import lombok.NonNull;

public enum ConfirmationCacheKeyBuilder {

    REMOVE_LOG_CHAT("rem_logchat"),

    REMOVE_ADMIN_CHAT("rem_adminchat"),

    SET_ADMIN_CHAT("set_adminchat");


    private final String prefix;

    ConfirmationCacheKeyBuilder(String prefix) {
        this.prefix=prefix;
    }

    public String buildKey(long chatId, long userId) {

        return prefix+":"+ chatId +":"+userId;
    }



}
