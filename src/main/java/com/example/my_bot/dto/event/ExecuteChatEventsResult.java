package com.example.my_bot.dto.event;

import lombok.Getter;


@Getter
public class ExecuteChatEventsResult {

    private final int executedEventsCounter;

    public ExecuteChatEventsResult(int executedEventsCounter) {
        this.executedEventsCounter = executedEventsCounter;
    }
}

