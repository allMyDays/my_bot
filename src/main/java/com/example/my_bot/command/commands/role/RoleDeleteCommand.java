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

import static com.example.my_bot.constant.MessageConstant.NOT_ENOUGH_ARGUMENTS_MESSAGE;
import static com.example.my_bot.constant.MessageConstant.NOT_VALID_INTEGER_MESSAGE;
import static com.example.my_bot.enumeration.DefaultRole.SENIOR_ADMINISTRATOR;
import static com.example.my_bot.utils.ChatUtils.isNumber;
import static com.example.my_bot.utils.ChatUtils.isValidInteger;

@Slf4j
@Command(mainCommandName = "удалитьроль", alternativeCommandNames = {"remrole"}, defaultRole = SENIOR_ADMINISTRATOR, eventable = false)
@RequiredArgsConstructor
public class RoleDeleteCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(2,60*3);

    private final VkChatClient vkChatClient;

    private final RoleService roleService;


    @Override
    public void execute(CommandMessageDto messageDto) throws ClientException, ApiException {

        long chatId = messageDto.getChatId();
        String[] args = messageDto.getFirstRowArguments();
        long peerId = messageDto.getPeerId();

        if(args.length==0){
            vkChatClient.sendText(NOT_ENOUGH_ARGUMENTS_MESSAGE,peerId, true);
            return;
        }

        RoleDto assignedRole;

        if(isNumber(args[0])&&!isValidInteger(args[0])){
            vkChatClient.sendText(NOT_VALID_INTEGER_MESSAGE,peerId, true);
            return;
        }

        try{
            if(isNumber(args[0])){
                assignedRole = roleService.deleteCustomRole(chatId, messageDto.getFromId(), Integer.parseInt(args[0]));
            }else{
                assignedRole = roleService.deleteCustomRole(chatId, messageDto.getFromId(), String.join(" ", args));
            }

        } catch (RoleException e) {
            vkChatClient.sendText(e.getMessage(),peerId, true);
            return;
        }

        vkChatClient.sendText(
                "✅Вы успешно удалили указанную роль. Все участники с этой ролью автоматически получили роль «%s» с приоритетом %d."
                        .formatted(assignedRole.getRoleName(), assignedRole.getRolePriority()),
                peerId,
                true);

        }


}
