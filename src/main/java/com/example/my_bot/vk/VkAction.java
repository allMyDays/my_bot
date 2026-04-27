package com.example.my_bot.vk;

import com.example.my_bot.vk.enumeration.VkActionType;
import com.google.gson.annotations.SerializedName;
import com.vk.api.sdk.objects.annotations.Required;
import com.vk.api.sdk.objects.messages.MessageActionStatus;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VkAction {

    @SerializedName("member_id")
    private Long memberId;

    @SerializedName("type")
    private VkActionType type;



}
