package com.example.my_bot.command.commands.limit;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.entity.RoleRateLimitEntity;
import com.example.my_bot.exception.command.CommandException;
import com.example.my_bot.exception.limit.RateLimitException;
import com.example.my_bot.exception.role.RoleException;
import com.example.my_bot.mapper.MessageMapper;
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
import static com.example.my_bot.utils.TextUtils.*;

@Slf4j
@Command(mainCommandName = "лимит", alternativeCommandNames = {"rolelimit"}, defaultRole = ADMINISTRATOR, eventable = false)
@RequiredArgsConstructor
public class RoleRateLimitCreateCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(3,60);

    private final VkChatClient vkChatClient;

    private final RoleRateLimitService roleRateLimitService;

    private final RoleService roleService;

    private final MessageMapper messageMapper;


    @Override
    public void execute(CommandMessageDto messageDto) throws ClientException, ApiException {

        long chatId = messageDto.getChatId();
        String[] args = messageDto.getFirstRowArguments();
        long fromId = messageDto.getFromId();
        long peerId = messageDto.getPeerId();

        SendMessageDto sendMessage = messageMapper.toSendMessageDto("", messageDto);


        if(args.length<5){                       // !лимит !пинг 3 6 часов 80
            sendMessage.setText(NOT_ENOUGH_ARGUMENTS_MESSAGE);
            vkChatClient.sendText(sendMessage);
            return;
        }

        if(!isValidInteger(args[1])||(isNumber(args[4])&&!isValidInteger(args[4]))){
                sendMessage.setText(NOT_VALID_INTEGER_MESSAGE);
                vkChatClient.sendText(sendMessage);
                return;
        }
        Optional<Long> timePeriodInSeconds =  TimeUtils.toSecondsFromString(args[2],args[3]);
        if(timePeriodInSeconds.isEmpty()){
            sendMessage.setText(INVALID_TIME_PERIOD_MESSAGE);
            vkChatClient.sendText(sendMessage);
            return;
        }

        RoleRateLimitEntity createdLimit;
        try{
            int maxUsage = Integer.parseInt(args[1]);
            boolean isPersonal = args.length==6&&args[5].equalsIgnoreCase("личный");

            if(isNumber(args[4])){
                createdLimit = roleRateLimitService.createCommandRateLimit(chatId,fromId,args[0],Integer.parseInt(args[4]), maxUsage,timePeriodInSeconds.get(),isPersonal);
            }else{
                createdLimit = roleRateLimitService.createCommandRateLimit(chatId,fromId,args[0],args[4], maxUsage,timePeriodInSeconds.get(),isPersonal);
            }
        }catch (RateLimitException | RoleException | CommandException e){
            sendMessage.setText(e.getMessage());
            vkChatClient.sendText(sendMessage);
            return;
        }
        if(createdLimit==null){
            log.error("chat {} error: createdLimit is null after executing method createCommandLimit", chatId);
            sendMessage.setText("Произошла ошибка при попытке обработать команды.");
            vkChatClient.sendText(sendMessage);
            return;
        }
        String result = "✅Успешно добавлен новый лимит в %d использований за %s для команды «%s», воздействующий только на роль «%s»."
                .formatted(createdLimit.getMaxUsage(), TimeUtils.formatDurationFromSeconds(createdLimit.getTimePeriodSec(),true),
                        createdLimit.getCommandName(), roleService.getRoleName(chatId,createdLimit.getRolePriority()).orElse("unknown"));

        if(createdLimit.isPersonal()){
            result+="\n❗Лимит будет считаться индивидуально на каждого участника с указанной ролью.";
        }else{
            result+="\n❗Вы не указали параметр «личный», поэтому лимит будет общим на всех участников с указанной ролью.";
        }

        sendMessage.setText(result);
        vkChatClient.sendText(sendMessage);

    }


}
