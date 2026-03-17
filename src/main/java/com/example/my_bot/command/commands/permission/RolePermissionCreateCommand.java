package com.example.my_bot.command.commands.permission;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.permission.SetCommandPermissionResult;
import com.example.my_bot.exception.command.CommandException;
import com.example.my_bot.exception.permission.PermissionException;
import com.example.my_bot.exception.role.RoleException;
import com.example.my_bot.service.ChatService;
import com.example.my_bot.service.RolePermissionService;
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
import static com.example.my_bot.utils.VkChatUtils.isNumber;
import static com.example.my_bot.utils.VkChatUtils.isValidInteger;

@Slf4j
@Command(mainCommandName = "разрешить", alternativeCommandNames = {"allow"}, defaultRole = ADMINISTRATOR, eventable = false)
@RequiredArgsConstructor
public class RolePermissionCreateCommand implements ChatCommand {

    private final VkChatClient vkChatClient;

    private final RolePermissionService permissionService;

    private final ChatService chatService;


    @Override
    public void execute(CommandMessageDto commandMessage) throws ClientException, ApiException {

        long chatId = commandMessage.getChatId();
        String[] args = commandMessage.getFirstRowArguments();

        if(args.length<2){
            vkChatClient.sendText(chatId, NOT_ENOUGH_ARGUMENTS_MESSAGE, true);
            return;
        }
        if(isNumber(args[0])&&!isValidInteger(args[0])){
                vkChatClient.sendText(chatId, NOT_VALID_INTEGER_MESSAGE, true);
                return;
        }

        SetCommandPermissionResult permissionResult=null;
        try{
            Set<String> userCommandsToProcess = new HashSet<>();
            userCommandsToProcess.add(args[1].trim());
                for(int i=1;i<commandMessage.getAllRows().length;i++){
                    userCommandsToProcess.add(commandMessage.getAllRows()[i].trim());
                }
            if(isNumber(args[0])){
                permissionResult = permissionService.allowCommandForRole(chatId, commandMessage.getFromId(), userCommandsToProcess,Integer.parseInt(args[0]));
            }else{
                permissionResult = permissionService.allowCommandForRole(chatId, commandMessage.getFromId(), userCommandsToProcess,args[0]);
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
        String cmdPrefix = "⚙ " + chatPrefix;

        appendSection(result, permissionResult.getAccepted(), cmdPrefix,
                "✅Команды:\n", "%s\nТеперь могут применяться только участниками с ролью «%s» и выше.", roleName);
        appendSection(result, permissionResult.getHasRequiredPermissionAlready(), cmdPrefix,
                "‼Команды:\n", "%s\nУже разрешены для роли «%s» и выше.", roleName);
        appendSection(result, permissionResult.getForbiddenToEdit(), cmdPrefix,
                "\uD83D\uDEABКоманды:\n", "%s\nНедоступны вам для редактирования (сейчас их роль доступа выше Вашей роли).", roleName);
        appendSection(result, permissionResult.getNotEnoughSpaceToAddNew(), cmdPrefix,
                "\uD83D\uDEABДля команд:\n", "%s\nНе хватило свободного места для добавления.", roleName);
        appendSection(result, permissionResult.getNotFound(), "❓",
                "❌Аргументы:\n", "%s\nНе являются командами или написаны с опечатками.", roleName);

        vkChatClient.sendText(chatId, result.toString(), true);

    }

    private void appendSection(StringBuilder result,
                               Collection<String> items,
                               String itemPrefix,
                               String title,
                               String messageTemplate,
                               String roleName) {
        if (items.isEmpty()) return;
        if (!result.isEmpty()) result.append("\n\n");
        String itemsFormatted = items.stream()
                .map(item -> itemPrefix + item)
                .collect(Collectors.joining("\n"));
        result.append(title);
        result.append(String.format(messageTemplate, itemsFormatted, roleName));
    }

}
