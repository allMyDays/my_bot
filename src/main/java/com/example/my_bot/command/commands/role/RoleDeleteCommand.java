package com.example.my_bot.command.commands.role;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.RoleDto;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.enumeration.command.CommandExecutionStatus;
import com.example.my_bot.exception.role.RoleException;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.service.RoleService;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static com.example.my_bot.constant.MessageConstant.NOT_ENOUGH_ARGUMENTS_MESSAGE;
import static com.example.my_bot.constant.MessageConstant.NOT_VALID_INTEGER_MESSAGE;
import static com.example.my_bot.enumeration.command.CommandExecutionStatus.*;
import static com.example.my_bot.enumeration.DefaultRole.SENIOR_ADMINISTRATOR;
import static com.example.my_bot.enumeration.chat.AdminChatCommandExecutionMode.ALL_BOUND_CHATS_AT_ONCE;
import static com.example.my_bot.utils.TextUtils.isNumber;
import static com.example.my_bot.utils.TextUtils.isValidInteger;

@Slf4j
@Command(mainCommandName = "удалитьроль", alternativeCommandNames = {"remrole"}, defaultRole = SENIOR_ADMINISTRATOR, eventable = false, adminChatCommandExecutionMode = ALL_BOUND_CHATS_AT_ONCE)
@RequiredArgsConstructor
public class RoleDeleteCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(2,60*3);

    private final VkChatClient vkChatClient;

    private final RoleService roleService;

    private final MessageMapper messageMapper;


    @Override
    public CommandExecutionStatus execute(CommandMessageDto commandMessage) throws ClientException, ApiException {

        long chatId = commandMessage.getCommandRoutingData().getDataBaseChatId();
        String[] args = commandMessage.getFirstRowArguments();

        SendMessageDto sendMessage = messageMapper.toSendMessageDto(true, commandMessage);

        if(args.length==0){
            sendMessage.setText(NOT_ENOUGH_ARGUMENTS_MESSAGE);
            vkChatClient.sendText(sendMessage);
            return ARGUMENT_VALIDATION_ERROR;
        }

        RoleDto assignedRole;

        if(isNumber(args[0])&&!isValidInteger(args[0])){
            sendMessage.setText(NOT_VALID_INTEGER_MESSAGE);
            vkChatClient.sendText(sendMessage);
            return ARGUMENT_VALIDATION_ERROR;
        }

        try{
            if(isNumber(args[0])){
                assignedRole = roleService.deleteCustomRole(chatId, commandMessage.getFromId(), Integer.parseInt(args[0]));
            }
            else{
                assignedRole = roleService.deleteCustomRole(chatId, commandMessage.getFromId(), String.join(" ", args));
            }

        }catch (RoleException e){
            sendMessage.setText(e.getMessage());
            vkChatClient.sendText(sendMessage);
            return BUSINESS_LOGIC_ERROR;
        }

        sendMessage.setText( "✅Вы успешно удалили указанную роль. Все участники с этой ролью автоматически получили роль «%s» с приоритетом %d."
                .formatted(assignedRole.getRoleName(), assignedRole.getRolePriority()));

        vkChatClient.sendText(sendMessage);
        return SUCCESS;
    }

}
