package com.example.my_bot.controller;

import com.example.my_bot.handler.AsyncEventHandler;
import com.example.my_bot.vk.VkMessageNew;
import com.example.my_bot.vk.enumeration.VkEventType;
import com.google.gson.Gson;
import com.vk.api.sdk.objects.callback.MessageNew;
import com.vk.api.sdk.objects.callback.Type;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static com.example.my_bot.vk.enumeration.VkEventType.CONFIRMATION;
import static com.example.my_bot.vk.enumeration.VkEventType.MESSAGE_NEW;


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

        VkMessageNew event = new Gson().fromJson(body, VkMessageNew.class);

        VkEventType type = event.getType();
        if (CONFIRMATION.equals(type)) {
            return confirmationCode;
        }
        if (MESSAGE_NEW.equals(type)) {
            asyncEventHandler.handleMessageNew(event);

        }
        return "ok";
    }
}