package com.example.my_bot.cache.value;

import lombok.Getter;
import lombok.NonNull;
@Getter
public class MessageCounter {
    final String text;
    final int count;


    public MessageCounter(@NonNull String text, int count) {
        this.text = text;
        this.count = count;
    }





}
