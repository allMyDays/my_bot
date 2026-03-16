package com.example.my_bot.command.commands.role;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.dto.RoleDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.entity.RoleEntity;
import com.example.my_bot.exception.role.RoleException;
import com.example.my_bot.service.RoleService;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;

import static com.example.my_bot.constant.MessageConstant.NOT_ENOUGH_ARGUMENTS_MESSAGE;
import static com.example.my_bot.constant.MessageConstant.NOT_VALID_INTEGER_MESSAGE;
import static com.example.my_bot.enumeration.DefaultRole.SENIOR_ADMINISTRATOR;
import static com.example.my_bot.utils.VkChatUtils.isNumber;
import static com.example.my_bot.utils.VkChatUtils.isValidInteger;

@Slf4j
@Command(mainCommandName = "удалитьроль", alternativeCommandNames = {"remrole"}, defaultRole = SENIOR_ADMINISTRATOR, eventable = false)
@RequiredArgsConstructor
public class RoleDeleteCommand implements ChatCommand {

    private final VkChatClient vkChatClient;

    private final RoleService roleService;


    @Override
    public void execute(CommandMessageDto cmd) throws ClientException, ApiException {

        long chatId = cmd.getChatId();
        String[] args = cmd.getFirstRowArguments();

        if(args.length==0){
            vkChatClient.sendText(chatId, NOT_ENOUGH_ARGUMENTS_MESSAGE, true);
            return;
        }

        RoleDto assignedRole=null;

        if(isNumber(args[0])&&!isValidInteger(args[0])){
            vkChatClient.sendText(chatId, NOT_VALID_INTEGER_MESSAGE, true);
            return;
        }

        try{
            if(isNumber(args[0])){
                assignedRole = roleService.deleteCustomRole(chatId, cmd.getFromId(), Integer.parseInt(args[0]));
            }else{
                assignedRole = roleService.deleteCustomRole(chatId, cmd.getFromId(), String.join(" ", args));
            }

        } catch (RoleException e) {
            vkChatClient.sendText(chatId, e.getMessage(), true);
            return;
        }

        vkChatClient.sendText(chatId,
                "✅Вы успешно удалили указанную роль. Все участники с этой ролью автоматически получили роль «%s» с приоритетом %d."
                        .formatted(assignedRole.getRoleName(), assignedRole.getRolePriority()), true);

        }


}
