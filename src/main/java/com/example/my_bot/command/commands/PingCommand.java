package com.example.my_bot.command.commands;


import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.utils.TextUtils;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import static com.example.my_bot.enumeration.DefaultRole.MEMBER;

@Slf4j
@Command(mainCommandName = "пинг", alternativeCommandNames = {"ping"}, defaultRole = MEMBER, eventable = true, onlyForConversations = false)
@RequiredArgsConstructor
public class PingCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(10,60*2);

    private VkChatClient vkChatClient;

    private final MessageMapper messageMapper;

    @Autowired
    @Lazy
    public void setVkChatClient(VkChatClient vkChatClient) {
        this.vkChatClient = vkChatClient;
    }

    @Override
    public void execute(CommandMessageDto messageDto) throws ClientException, ApiException {

       vkChatClient.sendText(messageMapper.toSendMessageDto("ПОНГ",messageDto));

    }
}
