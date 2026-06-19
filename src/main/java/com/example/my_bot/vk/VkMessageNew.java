package com.example.my_bot.vk;

import com.example.my_bot.vk.enumeration.VkEventType;
import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VkMessageNew {

    @SerializedName("group_id")
    private long groupId;

    @SerializedName("type")
    private VkEventType type;

    @SerializedName("object")
    private VkMessageObject messageObject;

    @SerializedName("secret")
    private String secretKey;







}
