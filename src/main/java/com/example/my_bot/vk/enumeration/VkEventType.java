package com.example.my_bot.vk.enumeration;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;

public enum VkEventType {

    @SerializedName("confirmation")
    CONFIRMATION("confirmation"),

    @SerializedName("message_new")
    MESSAGE_NEW("message_new"),

    @SerializedName("message_reaction_event")
    MESSAGE_REACTION_EVENT("message_reaction_event");

    @Getter
    private final String value;

    private VkEventType(String value) {
        this.value = value;
    }

}
