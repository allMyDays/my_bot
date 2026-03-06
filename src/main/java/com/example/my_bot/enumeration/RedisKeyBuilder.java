package com.example.my_bot.enumeration;

public enum RedisKeyBuilder {

    CHAT("chat:"), STAFF("staff:");

    private final String prefix;

    RedisKeyBuilder(String prefix) {
        this.prefix=prefix;
    }

    public String buildKey(long chatId) {
        return prefix + chatId;
    }



}
