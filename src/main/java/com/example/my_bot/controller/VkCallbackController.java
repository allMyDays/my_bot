package com.example.my_bot.controller;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.CommandDispatcher;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vk.api.sdk.client.TransportClient;
import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.httpclient.HttpTransportClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
public class VkCallbackController {

    private final String confirmationCode;

    private final CommandDispatcher commandDispatcher;

    private final long groupId;

    private final VkChatClient vkChatClient;

    public VkCallbackController(
            @Value("${vk.group.confirmation}") String confirmationCode,
            @Value("${vk.group.id}") long groupId,
            CommandDispatcher commandDispatcher, VkChatClient vkChatClient) {
        this.confirmationCode = confirmationCode;
        this.commandDispatcher=commandDispatcher;
        this.groupId = groupId;
        this.vkChatClient = vkChatClient;
    }

    @PostMapping("/callback")
    public String handle(@RequestBody String body) {


        JsonObject event = JsonParser.parseString(body).getAsJsonObject();
        String type = event.get("type").getAsString();

        if ("confirmation".equals(type)) {
            return confirmationCode;
        }

        if ("message_new".equals(type)) {
            JsonObject message = event.getAsJsonObject("object").getAsJsonObject("message");
            String text = message.get("text").getAsString();
            long peerId = message.get("peer_id").getAsLong();
            long fromId = message.get("from_id").getAsLong();

            try {
                JsonObject action = message.getAsJsonObject("action");
                if (action != null) {
                    String actionType = action.get("type").getAsString();
                    long memberId = action.get("member_id").getAsLong();
                    if ("chat_invite_user".equals(actionType) && memberId == -groupId) {
                        vkChatClient.sendWelcomeMessage(peerId);
                        return "ok";
                    }


                }
                commandDispatcher.dispatch(text, peerId, fromId);

            }catch (Exception e) {
                log.error("Произошла ошибка: ",e);

                try {
                    vkChatClient.sendUnknownErrorException(peerId);
                } catch (ClientException|ApiException e2) {
                    log.error("Ошибка при попытке отправить сообщение об ошибке в чат: ",e2);

                }
            }
        }
        return "ok";
    }
}