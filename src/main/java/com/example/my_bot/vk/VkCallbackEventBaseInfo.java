package com.example.my_bot.vk;

import com.example.my_bot.vk.enumeration.VkEventType;
import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VkCallbackEventBaseInfo {

    @SerializedName("type")
    private VkEventType type;

    @SerializedName("group_id")
    private Long groupId;

    @SerializedName("secret")
    private String secretKey;

}
