package com.example.my_bot.enumeration;

public enum RedisKeyBuilder {

    CHAT("chat:"), STAFF("staff:"), ROLE_CMD_PERMISSION("role_perm:");

    private final String prefix;

    private final static String suffix = ":";

    RedisKeyBuilder(String prefix) {
        this.prefix=prefix;
    }

    public String buildKey(long chatId) {
        return prefix + chatId + suffix;
    }



}
