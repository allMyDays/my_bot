package com.example.my_bot.vk.mapping.reaction;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.Setter;

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
