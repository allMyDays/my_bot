package com.example.my_bot.vk.mapping.attachment;

import com.example.my_bot.vk.enumeration.VideoType;
import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.Setter;


public class Video {

    @Getter
    @Setter
    @SerializedName("type")
    private VideoType type;
}
