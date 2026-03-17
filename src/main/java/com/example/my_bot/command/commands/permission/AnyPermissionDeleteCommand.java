package com.example.my_bot.command.commands.permission;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.exception.command.CommandException;
import com.example.my_bot.exception.permission.PermissionException;
import com.example.my_bot.service.RolePermissionService;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static com.example.my_bot.constant.MessageConstant.NOT_ENOUGH_ARGUMENTS_MESSAGE;
import static com.example.my_bot.enumeration.DefaultRole.ADMINISTRATOR;

@Slf4j
@Command(mainCommandName = "сброситьправо", alternativeCommandNames = {"ungrant"}, defaultRole = ADMINISTRATOR, eventable = false)
@RequiredArgsConstructor
public class AnyPermissionDeleteCommand implements ChatCommand {

    private final VkChatClient vkChatClient;

    private final RolePermissionService permissionService;



    @Override
    public void execute(CommandMessageDto commandMessage) throws ClientException, ApiException {

        long chatId = commandMessage.getChatId();
        String[] args = commandMessage.getFirstRowArguments();

        if(args.length==0){
            vkChatClient.sendText(chatId, NOT_ENOUGH_ARGUMENTS_MESSAGE, true);
            return;
        }

        try{
            permissionService.deleteCustomRolePermission(chatId, args[0], commandMessage.getFromId());
        }catch (CommandException | PermissionException e){
            vkChatClient.sendText(chatId, e.getMessage(), true);
            return;
        }

        vkChatClient.sendText(chatId, "✅Настройка прав для указанной команды была сброшена до дефолтной роли.", true);

    }

}
