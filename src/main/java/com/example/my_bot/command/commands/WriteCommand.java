package com.example.my_bot.command.commands;


import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.command.CommandRoutingData;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.resolver.UserInputResolver;
import com.example.my_bot.service.submanager.SubmanagerService;
import com.example.my_bot.utils.ChatUtils;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import static com.example.my_bot.constant.MessageConstant.THIS_COMMAND_IS_ONLY_FOR_CHATS_WITH_SUBMANAGERS;
import static com.example.my_bot.enumeration.DefaultRole.MEMBER;

@Slf4j
@Command(mainCommandName = "напиши", alternativeCommandNames = {"скажи","write"}, defaultRole = MEMBER, eventable = true)
@RequiredArgsConstructor
public class WriteCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(5000,60*2);

    private VkChatClient vkChatClient;
    private final MessageMapper messageMapper;
    private final SubmanagerService submanagerService;

    @Autowired
    @Lazy
    public void setVkChatClient(VkChatClient vkChatClient) {
        this.vkChatClient = vkChatClient;
    }

    @Override
    public void execute(CommandMessageDto commandMessage) throws ClientException, ApiException {

        CommandRoutingData routingData = new CommandRoutingData(commandMessage.getCommandRoutingData());

        if(!submanagerService.isSubmanager(routingData.getExecutorBot())){
            vkChatClient.sendText(messageMapper.toSendMessageDto(THIS_COMMAND_IS_ONLY_FOR_CHATS_WITH_SUBMANAGERS, routingData));
            return;
        }
        routingData.setResponderBot(routingData.getExecutorBot());
        routingData.setResponsePeerId(ChatUtils.convertToPeerId(routingData.getVkApiChatId()));

        if(commandMessage.getUserText().isEmpty()) return;

        String[] text = UserInputResolver.splitFullCommandIntoTwoElements(commandMessage.getUserText().get());
        if(text.length==1) return;

        boolean replyToMessageId = commandMessage.isReplyToMessageId();

        if(routingData.getOriginalEventPeerId()!=routingData.getResponsePeerId()){
            replyToMessageId = false;
        }

        vkChatClient.sendText(messageMapper.toSendMessageDto(text[1], routingData, commandMessage.getConversationMessageId(), replyToMessageId));

    }
}
