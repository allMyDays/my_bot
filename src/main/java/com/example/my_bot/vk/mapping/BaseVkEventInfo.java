package com.example.my_bot.vk.mapping;

import com.example.my_bot.vk.enumeration.VkEventType;
import com.google.gson.annotations.SerializedName;
import com.vk.api.sdk.objects.annotations.Required;
import com.vk.api.sdk.objects.callback.Type;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BaseVkEventInfo {

    @SerializedName("event_id")
    @Required
    private String eventId;
    @SerializedName("group_id")
    @Required
    private Long groupId;
    @SerializedName("secret")
    private String secret;
    @SerializedName("type")
    @Required
    private VkEventType type;
    @SerializedName("v")
    @Required
    private String v;

}
