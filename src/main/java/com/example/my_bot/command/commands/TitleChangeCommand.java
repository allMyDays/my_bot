package com.example.my_bot.command.commands;


import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.mapper.MessageMapper;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import static com.example.my_bot.constant.MessageConstant.NOT_ENOUGH_ARGUMENTS_MESSAGE;
import static com.example.my_bot.enumeration.DefaultRole.ADMINISTRATOR;


@Command(mainCommandName = "название", alternativeCommandNames = {"title"}, defaultRole = ADMINISTRATOR, eventable = true)
@Slf4j
@RequiredArgsConstructor
public class TitleChangeCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(3,60);

    private VkChatClient vkChatClient;

    private final MessageMapper messageMapper;

    @Autowired
    @Lazy
    public void setVkChatClient(VkChatClient vkChatClient) {
        this.vkChatClient = vkChatClient;
    }


    @Override
    public void execute(CommandMessageDto messageDto) throws ClientException, ApiException {

        if(messageDto.getFirstRowArguments().length==0){
            vkChatClient.sendText(
                    messageMapper.toSendMessageDto(NOT_ENOUGH_ARGUMENTS_MESSAGE, messageDto)
            );
            return;
        }
        vkChatClient.changeChatTitle(messageDto.getChatId(), String.join(" ", messageDto.getFirstRowArguments()));

    }

}
