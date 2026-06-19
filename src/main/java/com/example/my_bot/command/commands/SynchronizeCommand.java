package com.example.my_bot.command.commands;


import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.service.MemberService;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import static com.example.my_bot.constant.MessageConstant.*;
import static com.example.my_bot.enumeration.DefaultRole.MODERATOR;
import static com.example.my_bot.vk.enumeration.ChatErrorCode.*;

@Slf4j
@RequiredArgsConstructor
@Command(mainCommandName ="синхронизация", alternativeCommandNames = {"resync"}, defaultRole = MODERATOR, eventable = true)
public class SynchronizeCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(2,60*2);

    private VkChatClient vkChatClient;

    private final MemberService memberService;

    private final MessageMapper messageMapper;

    @Autowired
    @Lazy
    public void setVkChatClient(VkChatClient vkChatClient) {
        this.vkChatClient = vkChatClient;
    }



    @Override
    public void execute(CommandMessageDto commandMessage) throws ClientException, ApiException {

        SendMessageDto sendMessage = messageMapper.toSendMessageDto("",true, commandMessage);

        try{
            memberService.synchronizeChatMembers(commandMessage.getCommandRoutingData());
        }catch (ApiException e){
            if(NO_CHAT_ACCESS.getCodes().contains(e.getCode())){
                sendMessage.setText(THE_BOT_HAS_NO_CHAT_ACCESS_ERROR);
            }else{
                sendMessage.setText("Произошла ошибка: "+e.getMessage());
            }
            vkChatClient.sendText(sendMessage);
            return;
        }

        vkChatClient.sendText(
                messageMapper.toSendMessageDto("✅Текущие участники чата были синхронизированы с моей базой данных.",commandMessage));

    }
}
