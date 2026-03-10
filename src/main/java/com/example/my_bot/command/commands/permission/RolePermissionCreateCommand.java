package com.example.my_bot.command.commands.permission;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.entity.RoleEntity;
import com.example.my_bot.exception.command.CommandException;
import com.example.my_bot.exception.permission.PermissionException;
import com.example.my_bot.exception.role.RoleException;
import com.example.my_bot.service.ChatService;
import com.example.my_bot.service.CommandPermissionService;
import com.example.my_bot.service.RoleService;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

import static com.example.my_bot.constant.MessageConstant.NOT_ENOUGH_ARGUMENTS_MESSAGE;
import static com.example.my_bot.constant.MessageConstant.NOT_VALID_INTEGER_MESSAGE;
import static com.example.my_bot.constant.SettingConstant.DEFAULT_CHAT_PREFIX;
import static com.example.my_bot.enumeration.DefaultRole.ADMINISTRATOR;
import static com.example.my_bot.enumeration.DefaultRole.SENIOR_ADMINISTRATOR;
import static com.example.my_bot.utils.VkChatUtils.isNumber;
import static com.example.my_bot.utils.VkChatUtils.isValidInteger;

@Slf4j
@Command(mainCommandName = "разрешить", alternativeCommandNames = {"allow"}, defaultRole = ADMINISTRATOR, eventable = false)
@RequiredArgsConstructor
public class RolePermissionCreateCommand implements ChatCommand {

    private final VkChatClient vkChatClient;

    private final CommandPermissionService permissionService;

    private final ChatService chatService;


    @Override
    public void execute(long chatId, long fromId, String[] args) throws ClientException, ApiException {

        if(args.length<2){
            vkChatClient.sendText(chatId, NOT_ENOUGH_ARGUMENTS_MESSAGE, true);
            return;
        }
        if(isNumber(args[1])&&!isValidInteger(args[1])){
                vkChatClient.sendText(chatId, NOT_VALID_INTEGER_MESSAGE, true);
                return;
        }

        Set<String> processedCommands=null;
        try{
            Set<String> userCommandsToProcess = new HashSet<>();
            userCommandsToProcess.add(args[0].trim());
            if(args.length>2){
                for(int i=2;i<args.length;i++){
                    userCommandsToProcess.add(args[i].trim());
                }
            }
            if(isNumber(args[1])){
                processedCommands = permissionService.allowCommandForRole(chatId, fromId, userCommandsToProcess,Integer.parseInt(args[1]));
            }
        }catch (PermissionException | RoleException | CommandException e){
            vkChatClient.sendText(chatId, e.getMessage(), true);
            return;
        }
        if(processedCommands==null||processedCommands.isEmpty()){
            vkChatClient.sendText(chatId, "Данные не обновились. Либо эти команды уже доступны указанной роли, либо не существуют.", true);
            return;
        }

        char chatPrefix = chatService.getChatPrefix(chatId).orElse(DEFAULT_CHAT_PREFIX);

        if (processedCommands.size() == 1) {
            String command = processedCommands.iterator().next();
            vkChatClient.sendText(chatId, String.format("✅Команда «%c%s» теперь может применяться только участниками с указанной ролью и выше.", chatPrefix, command), true);
        } else if (processedCommands.size() > 1) {
            String commandsList = processedCommands.stream()
                    .map(cmd -> "⚙ " + chatPrefix + cmd)
                    .collect(Collectors.joining("\n"));
            String message = String.format("✅Команды:\n%s\nТеперь могут применяться только участниками с указанной ролью и выше.", commandsList);
            vkChatClient.sendText(chatId, message, true);
        }

    }
}
