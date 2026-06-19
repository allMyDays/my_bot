package com.example.my_bot.command.commands.timer;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.entity.TimerEntity;
import com.example.my_bot.exception.timer.TimerException;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.service.chat.ChatService;
import com.example.my_bot.service.timer.TimerService;
import com.example.my_bot.utils.TimeUtils;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.example.my_bot.constant.MessageConstant.*;
import static com.example.my_bot.enumeration.DefaultRole.ADMINISTRATOR;
import static com.example.my_bot.utils.TextUtils.isValidInteger;

@Slf4j
@Command(mainCommandName = "запусктаймера", alternativeCommandNames = {"timerlaunch"}, defaultRole = ADMINISTRATOR, eventable = false)
@RequiredArgsConstructor
public class TimerChangeNextExecutionCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(3,60);

    private final VkChatClient vkChatClient;

    private final TimerService timerService;

    private final ChatService chatService;

    private final MessageMapper messageMapper;


    @Override
    public void execute(CommandMessageDto commandMessage) throws ClientException, ApiException {

        long chatId = commandMessage.getCommandRoutingData().getDataBaseChatId();
        String[] args = commandMessage.getFirstRowArguments();

        SendMessageDto sendMessage = messageMapper.toSendMessageDto("",true, commandMessage);

        if(args.length<3){
            sendMessage.setText(NOT_ENOUGH_ARGUMENTS_MESSAGE);
            vkChatClient.sendText(sendMessage);
            return;
        }if(!isValidInteger(args[0])){
            sendMessage.setText(NOT_VALID_INTEGER_MESSAGE);
            vkChatClient.sendText(sendMessage);
            return;
        }
        Optional<LocalDateTime> localDateTimeOptional = TimeUtils.parseDateTime(args[1]+" "+args[2]);
        if(localDateTimeOptional.isEmpty()){
            sendMessage.setText(NOT_VALID_DATE_TIME);
            vkChatClient.sendText(sendMessage);
            return;
        }
        List<TimerEntity> timers;

        int outerTimerId = Integer.parseInt(args[0]);

        if(outerTimerId<1||outerTimerId>(timers = timerService.getChatTimersSortedByIdAsc(chatId)).size()){
            sendMessage.setText("Не найдено таймера с таким ID.");
            vkChatClient.sendText(sendMessage);
            return;
        }
        try{
            timerService.changeNextExecutionForEachTimer(timers.get(outerTimerId-1).getId(), localDateTimeOptional.get());
        }catch (TimerException e){
          sendMessage.setText(e.getMessage());
          vkChatClient.sendText(sendMessage);
          return;
        }
        String dateToShow = TimeUtils.getStringDateTimeWithTimeZone(localDateTimeOptional.get(), chatService.getChatTimeZone(chatId));
        sendMessage.setText("✅Теперь таймер с ID %d в следующий раз сработает %s.".formatted(outerTimerId, dateToShow));

        vkChatClient.sendText(sendMessage);
    }
}
