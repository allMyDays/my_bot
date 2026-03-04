package com.example.my_bot.command.impl;


import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.enumeration.DefaultRole;
import com.example.my_bot.service.ChatMemberService;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import static com.example.my_bot.utils.VkChatUtils.extractConversationId;

@Component
@Slf4j
@RequiredArgsConstructor
public class SynchronizeCommand implements ChatCommand {

    private VkChatClient vkChatClient;

    private final ChatMemberService memberService;

    @Autowired
    @Lazy
    public void setVkChatClient(VkChatClient vkChatClient) {
        this.vkChatClient = vkChatClient;
    }


    @Override
    public String getCommand() {
        return "синхронизация";
    }


    @Override
    public void execute(String message, long peerId, long fromId, String[] args) throws ClientException, ApiException {

        memberService.synchronizeChatMembers(extractConversationId(peerId));

        vkChatClient.sendText(peerId, "Данные участников были обновлены.");

    }
}
