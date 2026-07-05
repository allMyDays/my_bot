package com.example.my_bot.command.commands;


import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.command.CommandRoutingData;
import com.example.my_bot.enumeration.command.CommandExecutionStatus;
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

import static com.example.my_bot.constant.MessageConstant.NOT_ENOUGH_ARGUMENTS_MESSAGE;
import static com.example.my_bot.constant.MessageConstant.THIS_COMMAND_IS_ONLY_FOR_CHATS_WITH_SUBMANAGERS;
import static com.example.my_bot.enumeration.command.CommandExecutionStatus.*;
import static com.example.my_bot.enumeration.DefaultRole.MEMBER;
import static com.example.my_bot.enumeration.chat.AdminChatCommandExecutionMode.ALL_BOUND_CHATS_AT_ONCE;

@Slf4j
@Command(mainCommandName = "напиши", alternativeCommandNames = {"скажи","write"}, defaultRole = MEMBER, eventable = true, adminChatCommandExecutionMode = ALL_BOUND_CHATS_AT_ONCE)
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
    public CommandExecutionStatus execute(CommandMessageDto commandMessage) throws ClientException, ApiException {

        CommandRoutingData routingData = new CommandRoutingData(commandMessage.getCommandRoutingData());
        SendMessageDto sendMessage = messageMapper.toSendMessageDto(true, commandMessage);

        if(!submanagerService.isSubmanager(routingData.getExecutorBot())){
            sendMessage.setText(THIS_COMMAND_IS_ONLY_FOR_CHATS_WITH_SUBMANAGERS);
            vkChatClient.sendText(sendMessage);
            return BUSINESS_LOGIC_ERROR;
        }

        String[] text = UserInputResolver.splitFullCommandIntoTwoElements(commandMessage.getOptionalUserText().get());
        if(text.length==1){
            sendMessage.setText(NOT_ENOUGH_ARGUMENTS_MESSAGE);
            vkChatClient.sendText(sendMessage);
            return ARGUMENT_VALIDATION_ERROR;
        }

        routingData.setResponderBot(routingData.getExecutorBot());
        routingData.setResponsePeerId(ChatUtils.convertToPeerId(routingData.getVkApiChatId()));

        boolean replyToMessageId = commandMessage.isReplyToMessageId();

        if(routingData.getOriginalEventPeerId()!=routingData.getResponsePeerId()){
            replyToMessageId = false;
        }

        vkChatClient.sendText(messageMapper.toSendMessageDto(text[1], routingData, commandMessage.getConversationMessageId(), replyToMessageId));
        return SUCCESS;
    }
}
