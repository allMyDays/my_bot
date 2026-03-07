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

import static com.example.my_bot.constant.SettingConstant.DEFAULT_CHAT_PREFIX;


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

             String text;

             if(message==null||(text=message.trim()).isEmpty()) return;

             Character chatPrefix = chatService.getCachedChatDetails(chatId, true).getPrefix();

             boolean mustCutPrefix=true;

             if(chatPrefix!=null){
                 if(text.charAt(0)!=chatPrefix) return;
             }else{
                 if(text.charAt(0)!=DEFAULT_CHAT_PREFIX){
                     mustCutPrefix=false;

                 }
             }

             String[] parts = text.split("\\s+");
             String commandName = parts[0].toLowerCase();
              if(mustCutPrefix){
                  commandName = commandName.substring(1);
              }

             String[] arguments = Arrays.copyOfRange(parts, 1, parts.length);


        ChatCommand cmd = commands.get(commandName);
        if (cmd != null) {
            if(memberService.getCachedRolePriority(chatId,fromId)>=commandAnnotations.get(cmd).defaultRole().getRolePriority()){
                cmd.execute(message,chatId, fromId, arguments);
            }else{
                vkChatClient.sendText(chatId,"Ваша роль недостаточно высока для применения этой команды.", true);

            }
        }
    }





}
