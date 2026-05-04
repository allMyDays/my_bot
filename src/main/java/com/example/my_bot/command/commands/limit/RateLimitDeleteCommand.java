package com.example.my_bot.command.commands.limit;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.limit.RoleRateLimitDto;
import com.example.my_bot.exception.command.CommandException;
import com.example.my_bot.exception.limit.RateLimitException;
import com.example.my_bot.exception.role.RoleException;
import com.example.my_bot.service.RoleRateLimitService;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import static com.example.my_bot.constant.MessageConstant.NOT_ENOUGH_ARGUMENTS_MESSAGE;
import static com.example.my_bot.constant.MessageConstant.NOT_VALID_INTEGER_MESSAGE;
import static com.example.my_bot.enumeration.DefaultRole.ADMINISTRATOR;
import static com.example.my_bot.utils.TextUtils.isValidInteger;

@Slf4j
@Command(mainCommandName = "удалитьлимит", alternativeCommandNames = {"снятьлимит","remlimit"}, defaultRole = ADMINISTRATOR, eventable = false)
@RequiredArgsConstructor
public class RateLimitDeleteCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(3,60);

    private final VkChatClient vkChatClient;

    private final RoleRateLimitService roleRateLimitService;


    @Override
    public void execute(CommandMessageDto messageDto) throws ClientException, ApiException {

        long chatId = messageDto.getChatId();
        String[] args = messageDto.getFirstRowArguments();
        long fromId = messageDto.getFromId();
        long peerId = messageDto.getPeerId();

        if(args.length<1){
            vkChatClient.sendText(NOT_ENOUGH_ARGUMENTS_MESSAGE,peerId,true);
            return;
        }if(!isValidInteger(args[0])){
            vkChatClient.sendText(NOT_VALID_INTEGER_MESSAGE,peerId,true);
            return;

        } List<RoleRateLimitDto> roleLimits;

        int limitId = Integer.parseInt(args[0]);

        if(limitId<1||limitId>(roleLimits = roleRateLimitService.getRoleLimitsSortedByEntityId(chatId)).size()){
            vkChatClient.sendText("Не найдено лимита с таким ID.",peerId,true);
            return;
        }
        try{
            roleRateLimitService.deleteLimit(roleLimits.get(limitId-1),chatId,fromId);

        }catch (RateLimitException | RoleException | CommandException e){
          vkChatClient.sendText(e.getMessage(), peerId,true);
          return;
        } vkChatClient.sendText("✅Лимит с ID %d был успешно удалён.".formatted(limitId),peerId, true);
    }
}
