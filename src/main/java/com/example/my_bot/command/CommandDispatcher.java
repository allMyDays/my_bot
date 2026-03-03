package com.example.my_bot.command;

import com.example.my_bot.entity.ChatEntity;
import com.example.my_bot.service.ChatService;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.example.my_bot.utils.VkChatUtils.extractConversationId;
import static com.example.my_bot.utils.VkChatUtils.isPersonalChat;

@Component
public class CommandDispatcher {
    private final Map<String, BotCommand> commands = new HashMap<>();
    private final ChatService chatService;

    @Autowired
    public CommandDispatcher(List<BotCommand> commandList, ChatService chatService) {
        this.chatService = chatService;
        for (BotCommand cmd : commandList) {
            commands.put(cmd.getCommand(), cmd);
        }
    }



    public void dispatch(String message, long peerId, long fromId) throws ClientException, ApiException {

             if(isPersonalChat(peerId)) return;

             if(message==null||message.trim().isEmpty()) return;

             long chatId = extractConversationId(peerId);

             ChatEntity currentChat = chatService.getChatEntity(chatId).orElseGet(()->
                     chatService.createChatEntity(chatId,null));

             String text = message.trim();

             if(text.charAt(0)!=currentChat.getPrefix()) return;

             String[] parts = text.split("\\s+");
             String commandName = parts[0].substring(1).toLowerCase();

             String[] args = Arrays.copyOfRange(parts, 1, parts.length);


        BotCommand cmd = commands.get(commandName);
        if (cmd != null) {
            cmd.execute(message, peerId, fromId, args);
        } else {
           // Неизвестная команда??

        }


    }





}
