package com.example.my_bot.dto;

import jakarta.annotation.Nullable;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;


@Getter
@Setter
public class SendMessageDto {

    private String text;

    private long peerId;

    private Integer conversationMessageId;

    private boolean replyToMessageId;

    private boolean ableMentions;


    public SendMessageDto(@NonNull String text, long peerId, @Nullable Integer conversationMessageId, boolean replyToMessageId, boolean ableMentions) {
        this.text = text;
        this.peerId = peerId;
        this.conversationMessageId = conversationMessageId;
        this.replyToMessageId = replyToMessageId;
        this.ableMentions = ableMentions;
    }

    public void setText(@NonNull String text) {
        this.text = text;
    }
}
