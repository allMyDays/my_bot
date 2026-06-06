package com.example.my_bot.cache.key;

import lombok.NonNull;


public record UniqueStringKeyAndConversationMessageId(@NonNull String stringKey, int cmid){
}
