package com.example.my_bot.command;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.dto.permission.RoleCommandPermissionDto;
import com.example.my_bot.service.ChatService;
import com.example.my_bot.service.CommandPermissionService;
import com.example.my_bot.service.MemberService;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.example.my_bot.constant.MessageConstant.NOT_ENOUGH_ROLE_TO_EXECUTE_CMD;
import static com.example.my_bot.constant.SettingConstant.DEFAULT_CHAT_PREFIX;


@Component
@Slf4j
@RequiredArgsConstructor
public class CommandDispatcher {
    private final ChatService chatService;
    private final MemberService memberService;
    private final VkChatClient vkChatClient;
    private final CommandRegistry commandRegistry;
    private final CommandPermissionService cmdPermissionService;



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


        Optional<ChatCommand> cmdOptional = commandRegistry.getCommand(commandName);
        if (cmdOptional.isPresent()) {
            ChatCommand cmd = cmdOptional.get();
            Command cmdAnnotation = commandRegistry.getCommandAnnotation(commandName)
                    .orElseThrow(()->new RuntimeException("Cannot find required init-annotation @Command"));

            int userRolePriority = memberService.getCachedMemberRolePriority(chatId,fromId);

            RoleCommandPermissionDto customRolePermission = cmdPermissionService.getCachedCustomRolePermissions(chatId)
                    .get(cmdAnnotation.mainCommandName());

            boolean canExecute = false;
            if(customRolePermission!=null){
                if(userRolePriority>=customRolePermission.getRolePriority()){
                canExecute=true;
                }
            }
            else if(userRolePriority>=cmdAnnotation.defaultRole().getRolePriority()){
                canExecute=true;
            }

            if(canExecute){
                cmd.execute(chatId, fromId, arguments);
            }else{
                vkChatClient.sendText(chatId,NOT_ENOUGH_ROLE_TO_EXECUTE_CMD, true);
            }
        }
    }

}
