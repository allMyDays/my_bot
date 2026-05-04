package com.example.my_bot.cache.key;

import lombok.Getter;

@Getter
public class EventIdAndUniqueIdKey {
    private final long eventId;
    private final long uniqueKey;

    public EventIdAndUniqueIdKey(long eventId, long uniqueKey) {
        this.eventId = eventId;
        this.uniqueKey = uniqueKey;
    }
}
