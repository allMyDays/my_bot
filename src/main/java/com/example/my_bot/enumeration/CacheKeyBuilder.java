package com.example.my_bot.enumeration;

public enum CacheKeyBuilder {

    CHAT("chat:"), ROLE("role:"), STAFF("staff:"), ROLE_CMD_PERMISSION("role_perm:");

    private final String prefix;

    private final static String suffix = ":";

    CacheKeyBuilder(String prefix) {
        this.prefix=prefix;
    }

    public String buildKey(long chatId) {
        return prefix + chatId + suffix;
    }



}
