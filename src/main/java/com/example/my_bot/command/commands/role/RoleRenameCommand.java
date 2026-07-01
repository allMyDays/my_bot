package com.example.my_bot.command.commands.role;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.RoleDto;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.enumeration.CommandExecutionStatus;
import com.example.my_bot.exception.role.RoleException;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.service.RoleService;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;

import static com.example.my_bot.constant.MessageConstant.NOT_ENOUGH_ARGUMENTS_MESSAGE;
import static com.example.my_bot.constant.MessageConstant.NOT_VALID_INTEGER_MESSAGE;
import static com.example.my_bot.enumeration.CommandExecutionStatus.ARGUMENT_VALIDATION_ERROR;
import static com.example.my_bot.enumeration.CommandExecutionStatus.SUCCESS;
import static com.example.my_bot.enumeration.DefaultRole.SENIOR_ADMINISTRATOR;
import static com.example.my_bot.enumeration.chat.AdminChatCommandExecutionMode.ALL_BOUND_CHATS_AT_ONCE;
import static com.example.my_bot.utils.TextUtils.*;

@Slf4j
@Command(mainCommandName ="имяроли", alternativeCommandNames = {"renamerole"}, defaultRole = SENIOR_ADMINISTRATOR, eventable = false, adminChatCommandExecutionMode = ALL_BOUND_CHATS_AT_ONCE)
@RequiredArgsConstructor
public class RoleRenameCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(3,60*2);

    private final VkChatClient vkChatClient;

    private final RoleService roleService;

    private final MessageMapper messageMapper;


    @Override
    public CommandExecutionStatus execute(CommandMessageDto commandMessage) throws ClientException, ApiException {

        String[] args = commandMessage.getFirstRowArguments();
        long chatId = commandMessage.getCommandRoutingData().getDataBaseChatId();

        SendMessageDto sendMessage = messageMapper.toSendMessageDto(true, commandMessage);

        if(args.length<2){
            sendMessage.setText(NOT_ENOUGH_ARGUMENTS_MESSAGE);
            vkChatClient.sendText(sendMessage);
            return ARGUMENT_VALIDATION_ERROR;
        }
        RoleDto editedRole;

        if(isNumber(args[0])&&!isValidInteger(args[0])){
            sendMessage.setText(NOT_VALID_INTEGER_MESSAGE);
            vkChatClient.sendText(sendMessage);
            return ARGUMENT_VALIDATION_ERROR;
        }
        try{
            String newRoleName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            if(isNumber(args[0])){
                editedRole = roleService.renameRole(chatId, commandMessage.getFromId(), Integer.parseInt(args[0]),newRoleName);
            }
            else{
                editedRole = roleService.renameRole(chatId, commandMessage.getFromId(), args[0], newRoleName);
            }

        }catch (RoleException e){
            sendMessage.setText(e.getMessage());
            vkChatClient.sendText(sendMessage);
            return CommandExecutionStatus.BUSINESS_LOGIC_ERROR;
        }

        sendMessage.setText("✅Вы успешно переименовали указанную роль с приоритетом %d в «%s»."
                .formatted(editedRole.getRolePriority(), editedRole.getRoleName()));

        vkChatClient.sendText(sendMessage);
        return SUCCESS;

    }

}
