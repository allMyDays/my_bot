package com.example.my_bot.dto;

import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.objects.messages.Forward;
import com.vk.api.sdk.objects.messages.Keyboard;
import jakarta.annotation.Nullable;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;


@Getter
@Setter
public class SendMessageDto {

    private String text;

    private long responsePeerId;

    private GroupActor responderBot;

    private Integer conversationMessageId;

    private Long dataBaseChatId;

    private boolean replyToMessageId;

    private boolean ableMentions;

    private boolean doNotSendTheMessage;

    private Forward forward;

    private boolean isLogChatForward;

    private String attachment;

    private Keyboard keyboard;

    public SendMessageDto(@NonNull String text, long responsePeerId, @NonNull GroupActor responderBot, @Nullable Integer conversationMessageId, boolean replyToMessageId, boolean ableMentions, @Nullable Forward forward) {
        this.text = text;
        this.responsePeerId = responsePeerId;
        this.responderBot = responderBot;
        this.conversationMessageId = conversationMessageId;
        this.replyToMessageId = replyToMessageId;
        this.ableMentions = ableMentions;
        this.forward = forward;
    }

    public void setText(@NonNull String text) {
        this.text = text;
    }
}
