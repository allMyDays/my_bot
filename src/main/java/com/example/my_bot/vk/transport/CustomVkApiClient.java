package com.example.my_bot.vk.transport;

import com.google.gson.Gson;
import com.vk.api.sdk.actions.Messages;
import com.vk.api.sdk.client.TransportClient;
import com.vk.api.sdk.client.VkApiClient;

public class CustomVkApiClient extends VkApiClient {

    public CustomVkApiClient(TransportClient transportClient) {
        super(transportClient);
    }

    public CustomVkApiClient(TransportClient transportClient, Gson gson, int retryAttemptsInternalServerErrorCount) {
        super(transportClient, gson, retryAttemptsInternalServerErrorCount);
    }

    public CustomMessages messages() {
        return new CustomMessages(this);
    }



}
