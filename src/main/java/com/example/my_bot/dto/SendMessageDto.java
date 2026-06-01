package com.example.my_bot.dto;

import com.vk.api.sdk.objects.messages.Forward;
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

    private boolean doNotSendMessage;

    private Forward forward;

    private boolean isForwardedToLogChat;


    public SendMessageDto(@NonNull String text, long peerId, @Nullable Integer conversationMessageId, boolean replyToMessageId, boolean ableMentions,@Nullable Forward forward) {
        this.text = text;
        this.peerId = peerId;
        this.conversationMessageId = conversationMessageId;
        this.replyToMessageId = replyToMessageId;
        this.ableMentions = ableMentions;
        this.forward = forward;
    }

    public void setText(@NonNull String text) {
        this.text = text;
    }
}
