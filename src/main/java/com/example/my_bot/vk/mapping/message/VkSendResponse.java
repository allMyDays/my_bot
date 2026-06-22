package com.example.my_bot.vk.mapping.message;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class VkSendResponse {

    @SerializedName("response")
    public List<Item> response;
    public static class Item {

        @SerializedName("peer_id")
        public int peerId;

        @SerializedName("message_id")
        public int messageId;

        @SerializedName("conversation_message_id")
        public int conversationMessageId;
    }
}