package com.example.my_bot.command.impl;


import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import static com.example.my_bot.constant.MessageConstant.NOT_ENOUGH_ARGUMENTS_MESSAGE;

@Component
@Slf4j
public class TitleChangeCommand implements ChatCommand {

    private VkChatClient vkChatClient;

    @Autowired
    @Lazy
    public void setVkChatClient(VkChatClient vkChatClient) {
        this.vkChatClient = vkChatClient;
    }


    @Override
    public String getCommand() {
        return "название";
    }


    @Override
    public void execute(String message, long chatId, long fromId, String[] args) throws ClientException, ApiException {

        if(args.length==0){
            vkChatClient.sendText(chatId, NOT_ENOUGH_ARGUMENTS_MESSAGE,true);
            return;
        }
        vkChatClient.changeChatTitle(chatId, String.join(" ", args));

    }
}
