package com.example.my_bot.vk;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VkMessageObject {

    @SerializedName("message")
    private VkMessage message;

}
