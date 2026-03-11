package com.example.my_bot.command.commands.permission;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.dto.RoleDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.permission.SetCommandPermissionResult;
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

import javax.management.relation.Role;
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
    public void execute(CommandMessageDto commandMessage) throws ClientException, ApiException {

        long chatId = commandMessage.getChatId();
        String[] args = commandMessage.getFirstRowArguments();

        if(args.length<2){
            vkChatClient.sendText(chatId, NOT_ENOUGH_ARGUMENTS_MESSAGE, true);
            return;
        }
        if(isNumber(args[1])&&!isValidInteger(args[1])){
                vkChatClient.sendText(chatId, NOT_VALID_INTEGER_MESSAGE, true);
                return;
        }

        SetCommandPermissionResult permissionResult=null;
        try{
            Set<String> userCommandsToProcess = new HashSet<>();
            userCommandsToProcess.add(args[0].trim());

                for(int i=1;i<commandMessage.getAllRows().length;i++){
                    userCommandsToProcess.add(commandMessage.getAllRows()[i].trim());
                }


            if(isNumber(args[1])){
                permissionResult = permissionService.allowCommandForRole(chatId, commandMessage.getFromId(), userCommandsToProcess,Integer.parseInt(args[1]));
            }else{
                permissionResult = permissionService.allowCommandForRole(chatId, commandMessage.getFromId(), userCommandsToProcess,args[1]);
            }
        }catch (PermissionException | RoleException | CommandException e){
            vkChatClient.sendText(chatId, e.getMessage(), true);
            return;
        }
        if(permissionResult==null){
            vkChatClient.sendText(chatId, "Произошла ошибка при попытке обработать команды.", true);
            return;
        }
        char chatPrefix = chatService.getChatPrefix(chatId).orElse(DEFAULT_CHAT_PREFIX);

        StringBuilder result = new StringBuilder();
        String roleName = permissionResult.getRoleDto().getRoleName();

        if (!permissionResult.getChanged().isEmpty()) {
            String commandsList = permissionResult.getChanged().stream()
                    .map(cmd -> "⚙ " + chatPrefix + cmd)
                    .collect(Collectors.joining("\n"));
            result.append(String.format("✅Команды:\n%s\nТеперь могут применяться только участниками с ролью «%s» и выше.",
                    commandsList, roleName));
        }

        if (!permissionResult.getHasRequiredPermissionAlready().isEmpty()) {
            if (!result.isEmpty()) result.append("\n\n");
            String commandsList = permissionResult.getHasRequiredPermissionAlready().stream()
                    .map(cmd -> "⚙ " + chatPrefix + cmd)
                    .collect(Collectors.joining("\n"));
            result.append(String.format("‼Команды:\n%s\nУже разрешены для роли «%s» и выше.",
                    commandsList, roleName));
        }

        if (!permissionResult.getNotFound().isEmpty()) {
            if (!result.isEmpty()) result.append("\n\n");
            result.append(String.format("❌Аргументы:\n❓%s\nНе являются командами или написаны с опечатками.",
                    String.join("\n❓", permissionResult.getNotFound())));
        }
        vkChatClient.sendText(chatId, result.toString(), true);



    }

}
