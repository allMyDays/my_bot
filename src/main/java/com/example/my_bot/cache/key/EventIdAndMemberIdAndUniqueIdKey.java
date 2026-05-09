package com.example.my_bot.cache.key;

import lombok.Getter;
import lombok.NonNull;


public record EventIdAndMemberIdAndUniqueIdKey(@NonNull EventIdAndMemberIdKey eventIdAndMemberId, long uniqueKey){
}
