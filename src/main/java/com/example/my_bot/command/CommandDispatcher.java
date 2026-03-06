package com.example.my_bot.command;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.entity.ChatEntity;
import com.example.my_bot.service.ChatService;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.example.my_bot.utils.VkChatUtils.extractConversationId;
import static com.example.my_bot.utils.VkChatUtils.isPersonalChat;

@Component
@Slf4j
public class CommandDispatcher {
    private final Map<String, ChatCommand> commands = new HashMap<>();
    private final ChatService chatService;

    @Autowired
    public CommandDispatcher(List<ChatCommand> commandList, ChatService chatService) {
        this.chatService = chatService;
        for (ChatCommand cmd : commandList) {
            Command annotation = cmd.getClass().getAnnotation(Command.class);
            if (annotation != null) {
                for (String command : annotation.commands()) {
                    commands.put(command.toLowerCase(), cmd);
                }
            }else{
                log.error("Command with class %s does not have required init-annotation."
                        .formatted(cmd.getClass().getName()));

            }
        }
    }

    public void dispatch(String message, long chatId, long fromId) throws ClientException, ApiException {

             if(message==null||message.trim().isEmpty()) return;

             char prefix = chatService.getCachedChatDetails(chatId, true).getPrefix();

             String text = message.trim();

             if(text.charAt(0)!=prefix) return;

             String[] parts = text.split("\\s+");
             String commandName = parts[0].substring(1).toLowerCase();

             String[] args = Arrays.copyOfRange(parts, 1, parts.length);


        ChatCommand cmd = commands.get(commandName);
        if (cmd != null) {
            cmd.execute(message,chatId, fromId, args);
        }


    }





}
