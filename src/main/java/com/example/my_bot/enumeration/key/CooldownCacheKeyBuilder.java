package com.example.my_bot.enumeration.key;

import lombok.NonNull;

public enum CooldownCacheKeyBuilder {

    DEFAULT_COOLDOWN("def_cd:"), CUSTOM_ROLE_COOLDOWN("custom_role_cd:");

    private final String prefix;

    CooldownCacheKeyBuilder(String prefix) {
        this.prefix=prefix;
    }

    public String buildDefaultCDKey(long chatId, long userId, @NonNull String normalizedCommand) {
        if (this != DEFAULT_COOLDOWN) {
            throw new UnsupportedOperationException("buildDefaultCDKey поддерживается только для DEFAULT_COOLDOWN");
        }
        return prefix + chatId +":user"+userId+":"+normalizedCommand;
    }

    public String buildRolePersonalKey(long chatId, long userId, @NonNull String normalizedCommand, long limitEntityId) {
        if (this != CUSTOM_ROLE_COOLDOWN) {
            throw new UnsupportedOperationException(" buildRolePersonalKey поддерживается только для CUSTOM_ROLE_COOLDOWN");
        }
        return prefix + chatId +":user"+userId+":"+normalizedCommand+":"+limitEntityId;
    }

    public String buildRoleKey(long chatId, @NonNull String normalizedCommand, int rolePriority, long limitEntityId) {
        if (this != CUSTOM_ROLE_COOLDOWN) {
            throw new UnsupportedOperationException(" buildRoleKey поддерживается только для CUSTOM_ROLE_COOLDOWN");
        }
        return prefix + chatId +":role:"+rolePriority+":"+normalizedCommand+":"+limitEntityId;
    }



}
