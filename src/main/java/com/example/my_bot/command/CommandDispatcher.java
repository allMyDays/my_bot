package com.example.my_bot.command;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.service.ChatService;
import com.example.my_bot.service.MemberService;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;


@Component
@Slf4j
public class CommandDispatcher {
    private final Map<String, ChatCommand> commands = new HashMap<>();
    private final Map<ChatCommand, Command>  commandAnnotations = new HashMap<>();

    private final ChatService chatService;
    private final MemberService memberService;
    private final VkChatClient vkChatClient;

    @Autowired
    public CommandDispatcher(List<ChatCommand> chatCommandList,
                             ChatService chatService,
                             MemberService memberService,
                             VkChatClient vkChatClient) {
        this.chatService = chatService;
        this.memberService= memberService;
        this.vkChatClient=vkChatClient;
        for (ChatCommand chatCommand : chatCommandList) {
            Command annotation = chatCommand.getClass().getAnnotation(Command.class);
            if (annotation != null) {
                commandAnnotations.put(chatCommand, annotation);

                for (String stringCommand : annotation.commands()) {
                    commands.put(stringCommand.toLowerCase(), chatCommand);
                }
            }else{
                log.error("Command with class %s does not have required init-annotation."
                        .formatted(chatCommand.getClass().getName()));

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
            if(memberService.getCachedRolePriority(chatId,fromId)>=commandAnnotations.get(cmd).defaultRole().getRolePriority()){
                cmd.execute(message,chatId, fromId, args);
            }else{
                vkChatClient.sendText(chatId,"Ваша роль недостаточно высока для применения этой команды.", true);

            }
        }
    }





}
