package com.example.my_bot.command.commands.permission;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.permission.RolePermissionSettingResult;
import com.example.my_bot.exception.command.CommandException;
import com.example.my_bot.exception.permission.PermissionException;
import com.example.my_bot.exception.role.RoleException;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.service.chat.ChatService;
import com.example.my_bot.service.permission.RolePermissionService;
import com.example.my_bot.utils.ChatUtils;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

import static com.example.my_bot.constant.MessageConstant.NOT_ENOUGH_ARGUMENTS_MESSAGE;
import static com.example.my_bot.constant.MessageConstant.NOT_VALID_INTEGER_MESSAGE;
import static com.example.my_bot.enumeration.DefaultRole.ADMINISTRATOR;
import static com.example.my_bot.utils.TextUtils.*;

@Slf4j
@Command(mainCommandName = "правороли", alternativeCommandNames = {"roleallow"}, defaultRole = ADMINISTRATOR, eventable = false)
@RequiredArgsConstructor
public class RolePermissionCreateCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(3,60);

    private final VkChatClient vkChatClient;

    private final RolePermissionService permissionService;

    private final ChatService chatService;

    private final MessageMapper messageMapper;


    @Override
    public void execute(CommandMessageDto messageDto) throws ClientException, ApiException {

        long chatId = messageDto.getChatId();
        String[] args = messageDto.getFirstRowArguments();

        SendMessageDto sendMessage = messageMapper.toSendMessageDto("",true, messageDto);

        if(args.length<2){
            sendMessage.setText(NOT_ENOUGH_ARGUMENTS_MESSAGE);
            vkChatClient.sendText(sendMessage);
            return;
        }
        if(isNumber(args[0])&&!isValidInteger(args[0])){
                sendMessage.setText(NOT_VALID_INTEGER_MESSAGE);
                vkChatClient.sendText(sendMessage);
                return;
        }

        RolePermissionSettingResult permissionResult=null;
        try{
            Set<String> userCommandsToProcess = new HashSet<>();
            userCommandsToProcess.add(args[1].trim());
                for(int i=1;i<messageDto.getAllRows().length;i++){
                    userCommandsToProcess.add(messageDto.getAllRows()[i].trim());
                }
            if(isNumber(args[0])){
                permissionResult = permissionService.allowCommandForRole(chatId, messageDto.getFromId(), userCommandsToProcess,Integer.parseInt(args[0]));
            }else{
                permissionResult = permissionService.allowCommandForRole(chatId, messageDto.getFromId(), userCommandsToProcess,args[0]);
            }
        }catch (PermissionException | RoleException | CommandException e){
            sendMessage.setText(e.getMessage());
            vkChatClient.sendText(sendMessage);
            return;
        }
        if(permissionResult==null){
            log.error("chat {} error: permissionResult is null after executing method allowCommandForRole", chatId);
            sendMessage.setText("Произошла ошибка при попытке обработать команды.");
            vkChatClient.sendText(sendMessage);
            return;
        }
        char chatPrefix = chatService.getChatPrefix(chatId).orElse(ChatUtils.DEFAULT_CHAT_PREFIX);

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

        sendMessage.setText(result.toString());
        vkChatClient.sendText(sendMessage);

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
