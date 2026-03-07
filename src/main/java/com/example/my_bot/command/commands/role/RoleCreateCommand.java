package com.example.my_bot.command.commands.role;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
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
import static com.example.my_bot.enumeration.DefaultRole.MEMBER;
import static com.example.my_bot.enumeration.DefaultRole.SENIOR_ADMINISTRATOR;
import static com.example.my_bot.utils.VkChatUtils.isValidInteger;

@Slf4j
@Command(commands = {"новаяроль", "создатьроль", "рольсоздать"}, defaultRole = SENIOR_ADMINISTRATOR, eventable = false)
@RequiredArgsConstructor
public class RoleCreateCommand implements ChatCommand {

    private final VkChatClient vkChatClient;

    private final RoleService roleService;


    @Override
    public void execute(long chatId, long fromId, String[] args) throws ClientException, ApiException {

        if(args.length<2){
            vkChatClient.sendText(chatId, NOT_ENOUGH_ARGUMENTS_MESSAGE, true);
            return;
        }

        if(!isValidInteger(args[0])){
            vkChatClient.sendText(chatId, NOT_VALID_INTEGER_MESSAGE, true);
            return;
        }

        RoleEntity createdRole;
        try{
            createdRole =  roleService.createRole(chatId, fromId, Integer.parseInt(args[0]),
                   String.join(" ", Arrays.copyOfRange(args, 1, args.length)));

        }catch (RoleException e){
            vkChatClient.sendText(chatId, e.getMessage(), true);
            return;
        }
        if(createdRole!=null){
            vkChatClient.sendText(chatId, "✅Вы создали новую роль «%s» с приоритетом %d."
                    .formatted(createdRole.getRoleName(), createdRole.getRolePriority()), true);
        }
    }
}
