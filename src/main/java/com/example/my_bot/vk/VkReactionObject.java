package com.example.my_bot.vk;

import com.example.my_bot.vk.attachment.VkMessageAttachment;
import com.google.gson.annotations.SerializedName;
import com.vk.api.sdk.objects.messages.ForeignMessage;
import lombok.Getter;
import lombok.Setter;

import java.util.Collections;
import java.util.List;

@Getter
@Setter
public class VkReactionObject {

    @SerializedName("cmid")
    private int conversationMessageId;

    @SerializedName("reacted_id")
    private long reactedId;

    @SerializedName("peer_id")
    private long peerId;

    @SerializedName("reaction_id")
    private int reactionId;



}
