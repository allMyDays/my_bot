package com.example.my_bot.command.commands.role;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.entity.RoleEntity;
import com.example.my_bot.exception.role.RoleException;
import com.example.my_bot.service.RoleService;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;

import static com.example.my_bot.constant.MessageConstant.*;
import static com.example.my_bot.enumeration.DefaultRole.SENIOR_ADMINISTRATOR;
import static com.example.my_bot.utils.TextUtils.isValidInteger;

@Slf4j
@Command(mainCommandName = "создатьроль", alternativeCommandNames = {"новаяроль", "рольсоздать"}, defaultRole = SENIOR_ADMINISTRATOR, eventable = false)
@RequiredArgsConstructor
public class RoleCreateCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(5,60);

    private final VkChatClient vkChatClient;

    private final RoleService roleService;


    @Override
    public void execute(CommandMessageDto messageDto) throws ClientException, ApiException {

        long chatId = messageDto.getChatId();
        String[] args = messageDto.getFirstRowArguments();
        long peerId = messageDto.getPeerId();

        if(args.length<2){
            vkChatClient.sendText(NOT_ENOUGH_ARGUMENTS_MESSAGE,peerId, true);
            return;
        }

        if(!isValidInteger(args[0])){
            vkChatClient.sendText(NOT_VALID_INTEGER_MESSAGE, peerId,true);
            return;
        }

        RoleEntity createdRole=null;
        try{
            createdRole =  roleService.createRole(chatId, messageDto.getFromId(), Integer.parseInt(args[0]),
                   String.join(" ", Arrays.copyOfRange(args, 1, args.length)));

        }catch (RoleException e){
            vkChatClient.sendText(e.getMessage(), peerId,true);
            return;
        }
            vkChatClient.sendText("✅Вы успешно создали новую роль «%s» с приоритетом %d."
                    .formatted(createdRole.getRoleName(), createdRole.getRolePriority()),
                    peerId,
                    true);

    }
}
