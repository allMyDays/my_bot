package com.example.my_bot.command.commands.timer;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.entity.TimerEntity;
import com.example.my_bot.exception.timer.TimerException;
import com.example.my_bot.service.timer.TimerService;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import static com.example.my_bot.constant.MessageConstant.*;
import static com.example.my_bot.enumeration.DefaultRole.ADMINISTRATOR;
import static com.example.my_bot.utils.TextUtils.isValidInteger;

@Slf4j
@Command(mainCommandName = "лимиттаймера", alternativeCommandNames = {"timerlimit"}, defaultRole = ADMINISTRATOR, eventable = false)
@RequiredArgsConstructor
public class TimerChangeExecutionLimitCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(3,60);

    private final VkChatClient vkChatClient;

    private final TimerService timerService;


    @Override
    public void execute(CommandMessageDto messageDto) throws ClientException, ApiException {

        long chatId = messageDto.getChatId();
        String[] args = messageDto.getFirstRowArguments();
        long peerId = messageDto.getPeerId();

        if(args.length<2){
            vkChatClient.sendText(NOT_ENOUGH_ARGUMENTS_MESSAGE,peerId,true);
            return;
        }if(!isValidInteger(args[0])||!isValidInteger(args[1])){
            vkChatClient.sendText(NOT_VALID_INTEGER_MESSAGE,peerId,true);
            return;
        }
        List<TimerEntity> timers;

        int outerTimerId = Integer.parseInt(args[0]);

        if(outerTimerId<1||outerTimerId>(timers = timerService.getChatTimersSortedByIdAsc(chatId)).size()){
            vkChatClient.sendText("Не найдено таймера с таким ID.",peerId,true);
            return;
        }
        int newExecutionLimit = Integer.parseInt(args[1]);
        try{
            timerService.setCustomExecutionLimit(timers.get(outerTimerId-1).getId(), newExecutionLimit);
        }catch (TimerException e){
          vkChatClient.sendText(e.getMessage(), peerId,true);
          return;
        }
        String message = "✅Теперь таймер с ID %d выполнится максимум %d раз, после чего будет удалён."
                .formatted(outerTimerId, newExecutionLimit);

        vkChatClient.sendText(message,peerId, true);
    }
}
