package com.example.my_bot.vk.enumeration;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;

public enum VkMessageAttachmentType {

    @SerializedName("photo")
    PHOTO("photo"),
    @SerializedName("audio")
    AUDIO("audio"),
    @SerializedName("video")
    VIDEO("video"),
    @SerializedName("video_playlist")
    VIDEO_PLAYLIST("video_playlist"),
    @SerializedName("doc")
    DOC("doc"),
    @SerializedName("link")
    LINK("link"),
    @SerializedName("market")
    MARKET("market"),
    @SerializedName("gift")
    GIFT("gift"),
    @SerializedName("sticker")
    STICKER("sticker"),
    @SerializedName("wall")
    WALL("wall"),
    @SerializedName("wall_reply")
    WALL_REPLY("wall_reply"),
    @SerializedName("article")
    ARTICLE("article"),
    @SerializedName("poll")
    POLL("poll"),
    @SerializedName("call")
    CALL("call"),
    @SerializedName("graffiti")
    GRAFFITI("graffiti"),
    @SerializedName("audio_message")
    AUDIO_MESSAGE("audio_message");

    @Getter
    private final String value;

    VkMessageAttachmentType(String value) {
        this.value = value;
    }
}
