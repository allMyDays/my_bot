package com.example.my_bot.command;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.cooldown.CooldownResult;
import com.example.my_bot.service.*;
import com.google.common.collect.ImmutableMap;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.example.my_bot.constant.MessageConstant.NOT_ENOUGH_ROLE_TO_EXECUTE_CMD;
import static com.example.my_bot.constant.SettingConstant.DEFAULT_CHAT_PREFIX;
import static com.example.my_bot.utils.ChatUtils.createMention;
import static com.example.my_bot.utils.ChatUtils.formatDuration;


@Component
@Slf4j
@RequiredArgsConstructor
public class CommandDispatcher {
    private final ChatService chatService;
    private final MemberService memberService;
    private final VkChatClient vkChatClient;
    private final CommandRegistry commandRegistry;
    private final RolePermissionService cmdPermissionService;
    private final MemberPermissionService memberPermissionService;
    private final CooldownService cooldownService;



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

            ImmutableMap<Long, Boolean> memberPermissionsForCurrentCommand = memberPermissionService.getCachedCustomMemberPermissions(chatId)
                    .get(cmdAnnotation.mainCommandName());

            boolean canExecute = false;
            long fromId = commandMessage.getFromId();
            if(memberPermissionsForCurrentCommand!=null){
                Boolean currentUserPersonalAbilityToExecuteCommand = memberPermissionsForCurrentCommand.get(fromId);
                if(currentUserPersonalAbilityToExecuteCommand!=null){
                    canExecute = currentUserPersonalAbilityToExecuteCommand;
                    if(!canExecute){
                        vkChatClient.sendText(chatId,
                                createMention(fromId)+"(Вам) запрещена эта команда через индивидуальное разрешение.", true);
                        return;
                    }
                }
            }
           if(!canExecute){
              int userRolePriority = memberService.getCachedMemberRolePriority(chatId, fromId);
              Integer customRolePermissionPriority = cmdPermissionService.getCachedCustomRolePermissions(chatId)
                    .get(cmdAnnotation.mainCommandName());
              if(customRolePermissionPriority!=null){
                  canExecute = userRolePriority>=customRolePermissionPriority;
              }
              else if(userRolePriority>=cmdAnnotation.defaultRole().getRolePriority()){
                canExecute=true;
            }
           }

            if(canExecute){
                CooldownResult cooldownResult = cooldownService.tryConsume(chatId, fromId, cmdAnnotation.mainCommandName());
                if(!cooldownResult.isCanExecuteCommand()){
                  if(cooldownResult.isCanSendCDMessageToUser()){
                    vkChatClient.sendText(chatId,
                            "Нельзя так часто использовать эту команду. Она станет вновь доступна %s(Вам) через %s"
                                    .formatted(createMention(fromId),formatDuration(cooldownResult.getLeftCooldownSeconds(), true)),
                            true);
                  }return;
                }
                cmd.execute(commandMessage);
            }else{
                vkChatClient.sendText(chatId,NOT_ENOUGH_ROLE_TO_EXECUTE_CMD, true);
            }
        }
    }

}
