package com.example.my_bot.command;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.service.ChatService;
import com.example.my_bot.service.RolePermissionService;
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
    private final RolePermissionService cmdPermissionService;



    public void dispatch(CommandMessageDto commandMessage) throws ClientException, ApiException {

             Optional<String> commandOptional = commandMessage.getCommand();
             if(commandOptional.isEmpty()) return;
             String commandName = commandOptional.get();

             long chatId = commandMessage.getChatId();

             Optional<Character> chatPrefix = chatService.getCachedChatDetails(chatId, true)
                     .getOptionalPrefix();

             boolean mustCutPrefix=true;

             if(chatPrefix.isPresent()){
                 if(commandName.charAt(0)!=chatPrefix.get()) return;
             }else{
                 if(commandName.charAt(0)!=DEFAULT_CHAT_PREFIX){
                     mustCutPrefix=false;

                 }
             }
              if(mustCutPrefix){
                  commandName = commandName.substring(1);
              }


        Optional<ChatCommand> cmdOptional = commandRegistry.getCommand(commandName);
        if (cmdOptional.isPresent()) {
            ChatCommand cmd = cmdOptional.get();
            Command cmdAnnotation = commandRegistry.getCommandAnnotation(commandName)
                    .orElseThrow(()->new RuntimeException("Cannot find required init-annotation @Command"));

            int userRolePriority = memberService.getCachedMemberRolePriority(chatId, commandMessage.getFromId());

            Integer customRolePermissionPriority = cmdPermissionService.getCachedCustomRolePermissions(chatId)
                    .get(cmdAnnotation.mainCommandName());

            boolean canExecute = false;
            if(customRolePermissionPriority!=null){
                if(userRolePriority>=customRolePermissionPriority){
                canExecute=true;
                }
            }
            else if(userRolePriority>=cmdAnnotation.defaultRole().getRolePriority()){
                canExecute=true;
            }

            if(canExecute){
                cmd.execute(commandMessage);
            }else{
                vkChatClient.sendText(chatId,NOT_ENOUGH_ROLE_TO_EXECUTE_CMD, true);
            }
        }
    }

}
