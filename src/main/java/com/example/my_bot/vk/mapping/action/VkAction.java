package com.example.my_bot.vk.mapping.action;

import com.example.my_bot.vk.enumeration.VkActionType;
import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VkAction {

    @SerializedName("type")
    private VkActionType type;

    @SerializedName("member_id")
    private Long memberId;

    @SerializedName("text")
    private String text;
}
