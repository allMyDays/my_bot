package com.example.my_bot.command.commands.role;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.RoleDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.exception.role.RoleException;
import com.example.my_bot.service.RoleService;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;

import static com.example.my_bot.constant.MessageConstant.NOT_ENOUGH_ARGUMENTS_MESSAGE;
import static com.example.my_bot.constant.MessageConstant.NOT_VALID_INTEGER_MESSAGE;
import static com.example.my_bot.enumeration.DefaultRole.SENIOR_ADMINISTRATOR;
import static com.example.my_bot.utils.TextUtils.*;

@Slf4j
@Command(mainCommandName ="имяроли", alternativeCommandNames = {"renamerole"}, defaultRole = SENIOR_ADMINISTRATOR, eventable = false)
@RequiredArgsConstructor
public class RoleRenameCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(3,60*2);

    private final VkChatClient vkChatClient;

    private final RoleService roleService;


    @Override
    public void execute(CommandMessageDto messageDto) throws ClientException, ApiException {

        String[] args = messageDto.getFirstRowArguments();
        long chatId = messageDto.getChatId();
        long peerId = messageDto.getPeerId();

        if(args.length<2){
            vkChatClient.sendText(NOT_ENOUGH_ARGUMENTS_MESSAGE,peerId, true);
            return;
        }
        RoleDto editedRole=null;

        if(isNumber(args[0])&&!isValidInteger(args[0])){
            vkChatClient.sendText(NOT_VALID_INTEGER_MESSAGE,peerId, true);
            return;
        }
        try{
            String newRoleName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            if(isNumber(args[0])){
                editedRole = roleService.renameRole(chatId, messageDto.getFromId(), Integer.parseInt(args[0]),newRoleName);
            }else{
                editedRole = roleService.renameRole(chatId, messageDto.getFromId(), args[0], newRoleName);
            }

        } catch (RoleException e) {
            vkChatClient.sendText(e.getMessage(),peerId, true);
            return;
        }

        vkChatClient.sendText("✅Вы успешно переименовали указанную роль с приоритетом %d в «%s»."
                        .formatted(editedRole.getRolePriority(), editedRole.getRoleName()),
                peerId,
                true);

    }

}
