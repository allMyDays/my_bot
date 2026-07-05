package com.example.my_bot.command.commands;


import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.enumeration.command.CommandExecutionStatus;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.service.chat.ChatService;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import static com.example.my_bot.constant.MessageConstant.*;
import static com.example.my_bot.enumeration.command.CommandExecutionStatus.*;
import static com.example.my_bot.enumeration.DefaultRole.ADMINISTRATOR;
import static com.example.my_bot.enumeration.chat.AdminChatCommandExecutionMode.ALL_BOUND_CHATS_AT_ONCE;
import static com.example.my_bot.vk.enumeration.ChatErrorCode.YOU_ARE_NOT_CHAT_ADMIN;
import static com.example.my_bot.vk.enumeration.ChatErrorCode.YOU_ARE_RESTRICTED_TO_WRITE;


@Command(mainCommandName = "название", alternativeCommandNames = {"title"}, defaultRole = ADMINISTRATOR, eventable = true, adminChatCommandExecutionMode = ALL_BOUND_CHATS_AT_ONCE)
@Slf4j
@RequiredArgsConstructor
public class TitleChangeCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(3,60);

    private VkChatClient vkChatClient;
    private final MessageMapper messageMapper;
    private final ChatService chatService;

    @Autowired
    @Lazy
    public void setVkChatClient(VkChatClient vkChatClient) {
        this.vkChatClient = vkChatClient;
    }


    @Override
    public CommandExecutionStatus execute(CommandMessageDto commandMessage) throws ClientException, ApiException{

        SendMessageDto sendMessage = messageMapper.toSendMessageDto(true, commandMessage);
        long dataBaseChatId = commandMessage.getCommandRoutingData().getDataBaseChatId();

        if(commandMessage.getFirstRowArguments().length==0){
            sendMessage.setText(NOT_ENOUGH_ARGUMENTS_MESSAGE);
            vkChatClient.sendText(sendMessage);
            return ARGUMENT_VALIDATION_ERROR;
        }
        String title = String.join(" ", commandMessage.getFirstRowArguments());

        try{
            vkChatClient.changeChatTitle(
                    commandMessage.getCommandRoutingData().getExecutorBot(),
                    commandMessage.getCommandRoutingData().getVkApiChatId(),
                    title
            );
        }
        catch (ApiException e){
            if(YOU_ARE_RESTRICTED_TO_WRITE.getCodes().contains(e.getCode())){
                sendMessage.setText(THE_BOT_IS_RESTRICTED_TO_WRITE_ERROR );
            }
            else if(YOU_ARE_NOT_CHAT_ADMIN.getCodes().contains(e.getCode())){
                sendMessage.setText(THE_BOT_IS_NOT_CHAT_ADMIN_ERROR);
            }
            else{
                sendMessage.setText("Произошла ошибка: "+e.getMessage());
            }
            vkChatClient.sendText(sendMessage);
            return VK_API_ERROR;
        }
        chatService.setChatTitle(dataBaseChatId, title);
        return SUCCESS;
    }

}
