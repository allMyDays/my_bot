package com.example.my_bot.command.commands.permission;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.entity.RoleEntity;
import com.example.my_bot.exception.role.RoleException;
import com.example.my_bot.service.CommandPermissionService;
import com.example.my_bot.service.RoleService;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static com.example.my_bot.constant.MessageConstant.NOT_ENOUGH_ARGUMENTS_MESSAGE;
import static com.example.my_bot.constant.MessageConstant.NOT_VALID_INTEGER_MESSAGE;
import static com.example.my_bot.enumeration.DefaultRole.ADMINISTRATOR;
import static com.example.my_bot.enumeration.DefaultRole.SENIOR_ADMINISTRATOR;
import static com.example.my_bot.utils.VkChatUtils.isNumber;
import static com.example.my_bot.utils.VkChatUtils.isValidInteger;

@Slf4j
@Command(mainCommandName = "разрешить", alternativeCommandNames = {"allow"}, defaultRole = ADMINISTRATOR, eventable = false)
@RequiredArgsConstructor
public class RolePermissionCreateCommand implements ChatCommand {

    private final VkChatClient vkChatClient;

    private final CommandPermissionService permissionService;


    @Override
    public void execute(long chatId, long fromId, String[] args) throws ClientException, ApiException {

        if(args.length<2){
            vkChatClient.sendText(chatId, NOT_ENOUGH_ARGUMENTS_MESSAGE, true);
            return;
        }
        if(isNumber(args[1])){
            if(!isValidInteger(args[1])){
                vkChatClient.sendText(chatId, NOT_VALID_INTEGER_MESSAGE, true);
                return;
            }
        }

        Set<String> processedCommands=null;
        try{
            if(isNumber(args[1])){
                processedCommands = permissionService.allowCommandForRole(chatId, fromId, Set.of(args[0]),Integer.parseInt(args[1]));
            }
        }catch (RoleException e){
            vkChatClient.sendText(chatId, e.getMessage(), true);
            return;
        }
        if(processedCommands==null||processedCommands.isEmpty()){
            vkChatClient.sendText(chatId, "Похоже, что вы не ввели ни одной валидной команды. Проверьте правильность написанных команд.", true);
            return;
        }

        if(processedCommands.size()==1){
            vkChatClient.sendText(chatId, "✅Команда «%s» теперь может применяться только участниками с указанной ролью и выше."
                    .formatted(processedCommands.stream().findFirst().get()), true);
        }

    }
}
