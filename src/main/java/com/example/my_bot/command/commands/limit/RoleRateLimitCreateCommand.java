package com.example.my_bot.command.commands.limit;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.entity.RoleRateLimitEntity;
import com.example.my_bot.exception.command.CommandException;
import com.example.my_bot.exception.limit.RateLimitException;
import com.example.my_bot.exception.role.RoleException;
import com.example.my_bot.service.RoleRateLimitService;
import com.example.my_bot.service.RoleService;
import com.example.my_bot.utils.TimeUtils;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

import static com.example.my_bot.constant.MessageConstant.*;
import static com.example.my_bot.enumeration.DefaultRole.ADMINISTRATOR;
import static com.example.my_bot.utils.ChatUtils.*;

@Slf4j
@Command(mainCommandName = "лимит", alternativeCommandNames = {"rolelimit"}, defaultRole = ADMINISTRATOR, eventable = false)
@RequiredArgsConstructor
public class RoleRateLimitCreateCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(3,60);

    private final VkChatClient vkChatClient;

    private final RoleRateLimitService roleRateLimitService;

    private final RoleService roleService;


    @Override
    public void execute(CommandMessageDto messageDto) throws ClientException, ApiException {

        long chatId = messageDto.getChatId();
        String[] args = messageDto.getFirstRowArguments();
        long fromId = messageDto.getFromId();
        long peerId = messageDto.getPeerId();

        if(args.length<5){
            vkChatClient.sendText(NOT_ENOUGH_ARGUMENTS_MESSAGE,peerId, true);
            return;
        }

        if((isNumber(args[4])&&!isValidInteger(args[4]))){
                vkChatClient.sendText(NOT_VALID_INTEGER_MESSAGE,peerId, true);
                return;
        }
        Optional<Long> timePeriodInSeconds =  TimeUtils.toSecondsFromString(args[1],args[2]);
        if(timePeriodInSeconds.isEmpty()){
            vkChatClient.sendText(INVALID_TIME_PERIOD_MESSAGE, peerId,true);
            return;
        }

        RoleRateLimitEntity createdLimit=null;
        try{
            int maxUsage = Integer.parseInt(args[3]);
            boolean isPersonal = args.length==6&&args[5].equalsIgnoreCase("личный");

            if(isNumber(args[4])){
                createdLimit = roleRateLimitService.createCommandRateLimit(chatId,fromId,args[0],Integer.parseInt(args[4]), maxUsage,timePeriodInSeconds.get(),isPersonal);
            }else{
                createdLimit = roleRateLimitService.createCommandRateLimit(chatId,fromId,args[0],args[4], maxUsage,timePeriodInSeconds.get(),isPersonal);
            }
        }catch (RateLimitException | RoleException | CommandException e){
            vkChatClient.sendText(e.getMessage(),peerId, true);
            return;
        }
        if(createdLimit==null){
            log.error("chat {} error: createdLimit is null after executing method createCommandLimit", chatId);
            vkChatClient.sendText("Произошла ошибка при попытке обработать команды.",peerId, true);
            return;
        }
        String result = "✅Успешно добавлен новый лимит в %d использований за %s для команды «%s», воздействующий только на роль «%s»."
                .formatted(createdLimit.getMaxUsage(), TimeUtils.formatDurationFromSeconds(createdLimit.getPeriodInSeconds(),true),
                        createdLimit.getCommandName(), roleService.getRoleName(chatId,createdLimit.getRolePriority()).orElse("unknown"));

        if(createdLimit.isPersonal()){
            result+="\n❗Лимит будет считаться индивидуально на каждого участника с указанной ролью.";
        }else{
            result+="\n❗Вы не указали параметр «личный», поэтому лимит будет общим на всех участников с указанной ролью.";
        }

        vkChatClient.sendText(result,peerId, true);

    }


}
