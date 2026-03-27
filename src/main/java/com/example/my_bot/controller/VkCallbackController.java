package com.example.my_bot.controller;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.CommandDispatcher;
import com.example.my_bot.mapper.CommandMapper;
import com.example.my_bot.service.AsyncEventHandler;
import com.example.my_bot.service.GlobalUserService;
import com.example.my_bot.service.chat.ChatActionService;
import com.google.gson.Gson;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.objects.callback.MessageNew;
import com.vk.api.sdk.objects.callback.Type;
import com.vk.api.sdk.objects.messages.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

import static com.example.my_bot.constant.MessageConstant.UNKNOWN_ERROR_MESSAGE;
import static com.example.my_bot.utils.ChatUtils.extractConversationId;
import static com.example.my_bot.utils.ChatUtils.isPersonalChat;
import static com.vk.api.sdk.objects.callback.Type.CONFIRMATION;
import static com.vk.api.sdk.objects.callback.Type.MESSAGE_NEW;

@RestController
@Slf4j
public class VkCallbackController {

    private final String confirmationCode;

    private final AsyncEventHandler asyncEventHandler;

    public VkCallbackController(
            @Value("${vk.group.confirmation}")String confirmationCode,
            AsyncEventHandler asyncEventHandler) {

        this.confirmationCode = confirmationCode;
        this.asyncEventHandler = asyncEventHandler;
    }


    @PostMapping("/callback")
    public String handle(@RequestBody String body) {

        MessageNew event = new Gson().fromJson(body, MessageNew.class);

        Type type = event.getType();
        if (CONFIRMATION.equals(type)) {
            return confirmationCode;
        }
        if (MESSAGE_NEW.equals(type)) {
            asyncEventHandler.handleMessageNew(event);

        }
        return "ok";
    }
}