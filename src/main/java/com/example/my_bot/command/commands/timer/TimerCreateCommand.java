package com.example.my_bot.command.commands.timer;


import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.entity.TimerEntity;
import com.example.my_bot.exception.command.CommandException;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;
import java.util.regex.Pattern;

import static com.example.my_bot.constant.MessageConstant.*;
import static com.example.my_bot.enumeration.DefaultRole.SENIOR_MODERATOR;
import static com.example.my_bot.enumeration.timer.TimerType.*;
import static com.example.my_bot.utils.TextUtils.collectArgumentsSinceIndex;

@Slf4j
@RequiredArgsConstructor
@Command(mainCommandName = "таймер", alternativeCommandNames = {"timer"}, defaultRole = SENIOR_MODERATOR, eventable = false)
public class TimerCreateCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(4,60*2);

    private final TimerService timerService;

    private VkChatClient vkChatClient;

    private final ChatService chatService;

    private final MessageMapper messageMapper;

    @Autowired
    @Lazy
    public void setVkChatClient(VkChatClient vkChatClient) {
        this.vkChatClient = vkChatClient;
    }



    @Override
    public void execute(CommandMessageDto messageDto) throws ClientException, ApiException {

        String[] args = messageDto.getFirstRowArguments();
        long chatId = messageDto.getChatId();
        long fromId = messageDto.getFromId();

        SendMessageDto sendMessage = messageMapper.toSendMessageDto("",true, messageDto);

        if(args.length<3){
            sendMessage.setText(NOT_ENOUGH_ARGUMENTS_MESSAGE);
            vkChatClient.sendText(sendMessage);
            return;
        }
        String type = args[0].toLowerCase();
        TimerEntity createdTimer;

        try{
            if(DAILY.getCyrillicType().equals(type)){
                Optional<LocalTime> localTimeOptional = TimeUtils.parseTimeOfDay(args[1]);
                if(localTimeOptional.isEmpty()){
                    sendMessage.setText(NOT_VALID_TIME);
                    vkChatClient.sendText(sendMessage);
                    return;
                }createdTimer = timerService.createDailyTimer(
                        chatId, localTimeOptional.get(), collectArgumentsSinceIndex(args, 2), fromId);
            }
            else if(EACH.getCyrillicType().equals(type)){
                Optional<Long> intervalOptional = TimeUtils.parseManyHoursWithMinutes(args[1]);
                if(intervalOptional.isEmpty()){
                    sendMessage.setText(NOT_VALID_TIME);
                    vkChatClient.sendText(sendMessage);
                    return;
                }createdTimer = timerService.createEachTimer(
                        chatId, intervalOptional.get(),collectArgumentsSinceIndex(args, 2),fromId);

            }else if(ONCE.getCyrillicType().equals(type)){
                if(args.length<4){
                    sendMessage.setText(NOT_ENOUGH_ARGUMENTS_MESSAGE);
                    vkChatClient.sendText(sendMessage);
                    return;
                }Optional<LocalDateTime> localDateTimeOptional = TimeUtils.parseDateTime(args[1]+" "+args[2]);
                if(localDateTimeOptional.isEmpty()){
                    sendMessage.setText(NOT_VALID_DATE_TIME);
                    vkChatClient.sendText(sendMessage);
                    return;
                }createdTimer = timerService.createOnceTimer(
                        chatId, localDateTimeOptional.get(), collectArgumentsSinceIndex(args, 3), fromId);

            }else{
                sendMessage.setText("Вы ввели несуществующий тип таймера.");
                vkChatClient.sendText(sendMessage);
                return;
            }
        }catch (TimerException | CommandException e){
            sendMessage.setText(e.getMessage());
            vkChatClient.sendText(sendMessage);
            return;

        }

        String dateToShow = TimeUtils.getStringDateTimeWithTimeZone(
                createdTimer.getNextExecution(), chatService.getChatTimeZone(chatId));

        sendMessage.setText(("✅ Вы успешно создали новый таймер.\n" +
                "&#128218; Тип: %s (%s).\n".formatted(createdTimer.getType().getCyrillicType(),createdTimer.getType().getDescription() )+
                "&#128339; Дата следующего срабатывания — %s\n").formatted(dateToShow)+
                "&#8618; Команда: %s".formatted(createdTimer.getFullCommand()));

        vkChatClient.sendText(sendMessage);

    }
}
