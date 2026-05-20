package com.example.my_bot.vk;

import com.example.my_bot.vk.attachment.VkMessageAttachment;
import com.google.gson.annotations.SerializedName;
import com.vk.api.sdk.objects.annotations.Required;
import com.vk.api.sdk.objects.messages.ForeignMessage;
import com.vk.api.sdk.objects.messages.MessageAttachment;
import lombok.Getter;
import lombok.Setter;

import java.util.Collections;
import java.util.List;

@Getter
@Setter
public class VkMessage {

    @SerializedName("conversation_message_id")
    private int conversationMessageId;

    @SerializedName("date")
    private Integer date;

    @SerializedName("from_id")
    private long fromId;

    @SerializedName("peer_id")
    private long peerId;

    @SerializedName("text")
    private String text;

    @SerializedName("expire_ttl")
    private Integer expireTTL;

    @SerializedName("action")
    private VkAction action;

    @SerializedName("attachments")
    private List<VkMessageAttachment> attachments= Collections.emptyList();

    @SerializedName("fwd_messages")
    private List<ForeignMessage> fwdMessages= Collections.emptyList();

    @SerializedName("reply_message")
    private ForeignMessage replyMessage;

    @SerializedName("is_cropped")
    private boolean isCropped;

}
