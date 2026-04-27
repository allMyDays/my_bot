package com.example.my_bot.vk.enumeration;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;

public enum VideoType {

    @SerializedName("video")
    VIDEO("video"),

    @SerializedName("short_video")
    SHORT_VIDEO("short_video"),

    @SerializedName("video_message")
    VIDEO_MESSAGE("video_message");

    @Getter
    private final String value;

    VideoType(String value) {
        this.value = value;
    }
}
