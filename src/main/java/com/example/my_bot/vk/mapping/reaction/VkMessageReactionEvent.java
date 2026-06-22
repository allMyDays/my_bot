package com.example.my_bot.vk.mapping.reaction;

import com.example.my_bot.vk.enumeration.VkEventType;
import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VkMessageReactionEvent {

    @SerializedName("group_id")
    private long groupId;

    @SerializedName("type")
    private VkEventType type;

    @SerializedName("object")
    private VkReactionObject object;

    @SerializedName("secret")
    private String secretKey;
}
