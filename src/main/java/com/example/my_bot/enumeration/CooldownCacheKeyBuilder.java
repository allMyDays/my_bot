package com.example.my_bot.enumeration;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

public enum CooldownCacheKeyBuilder {

    DEFAULT_COOLDOWN("def_cd:");

    private final String prefix;

    CooldownCacheKeyBuilder(String prefix) {
        this.prefix=prefix;
    }

    public String buildKey(long chatId, long userId, @NonNull String normalizedCommand) {
        return prefix + chatId +":"+userId+":"+normalizedCommand;
    }



}
