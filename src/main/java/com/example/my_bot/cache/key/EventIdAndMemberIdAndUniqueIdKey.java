package com.example.my_bot.cache.key;

import lombok.Getter;

@Getter
public class EventIdAndMemberIdAndUniqueIdKey {
    private final long eventId;
    private final long memberId;
    private final long uniqueKey;

    public EventIdAndMemberIdAndUniqueIdKey(long eventId, long memberId, long uniqueKey) {
        this.eventId = eventId;
        this.memberId = memberId;
        this.uniqueKey = uniqueKey;
    }
}
