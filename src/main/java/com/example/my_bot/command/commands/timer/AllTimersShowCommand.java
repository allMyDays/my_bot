package com.example.my_bot.command.commands.timer;


import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.entity.TimerEntity;
import com.example.my_bot.enumeration.command.CommandExecutionStatus;
import com.example.my_bot.enumeration.TimeZoneType;
import com.example.my_bot.enumeration.timer.TimerType;
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

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.example.my_bot.enumeration.command.CommandExecutionStatus.SUCCESS;
import static com.example.my_bot.enumeration.DefaultRole.SENIOR_MODERATOR;
import static com.example.my_bot.enumeration.chat.AdminChatCommandExecutionMode.ONLY_SINGLE_BOUND_CHAT_AT_ONCE;
import static com.example.my_bot.enumeration.timer.TimerType.*;
import static com.example.my_bot.utils.TextUtils.createMention;
import static com.example.my_bot.utils.TimeUtils.formatDurationFromSeconds;

@Slf4j
@RequiredArgsConstructor
@Command(mainCommandName = "таймеры", alternativeCommandNames = {"timers"}, defaultRole = SENIOR_MODERATOR, eventable = false, adminChatCommandExecutionMode = ONLY_SINGLE_BOUND_CHAT_AT_ONCE)
public class AllTimersShowCommand implements ChatCommand {

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
    public CommandExecutionStatus execute(CommandMessageDto commandMessage) throws ClientException, ApiException {

        long chatId = commandMessage.getCommandRoutingData().getDataBaseChatId();

        List<TimerEntity> timers = timerService.getChatTimersSortedByIdAsc(chatId);

        StringBuilder sb = new StringBuilder();
        sb.append("В чате установлено (%d/%d) таймеров.\n\n".formatted(timers.size(), timerService.getMaxTimers()));
        int index =1;
        TimeZoneType chatTimeZone = chatService.getChatTimeZone(chatId);
        for(TimerEntity timer: timers){
            Instant nextExecution = timer.getNextExecution();

            TimerType type = timer.getType();
            sb.append("%s(%d)".formatted(createMention(timer.getCreatorId()),index++))
                    .append(". Команда «%s» ".formatted(timer.getFullCommand()));
                    if(type.equals(ONCE)){
                        sb.append("одноразово ");
                    }
                    else{
                        sb.append("циклично ");
                        if(type.equals(EACH)){
                            sb.append("через каждые ")
                                    .append(formatDurationFromSeconds(timer.getIntervalSeconds(), true));
                        }else{
                            sb.append("каждый день в ")
                                    .append(nextExecution.atZone(chatTimeZone.getZoneOffset()).toLocalTime())
                                    .append(".");
                        }
                        Optional<Integer> customExecutionLimit = timer.getOptionalCustomExecutionLimit();
                        customExecutionLimit.ifPresent(limit -> sb.append(" (%d/%d).".formatted(timer.getExecutionCounter(), limit)));
                        sb.append(" Следующий вызов: ");
                    }
                    sb.append(TimeUtils.getFormattedStringDateTimeWithTimeZone(nextExecution, chatTimeZone));
                    sb.append("\n");

        }
        vkChatClient.sendText(messageMapper.toSendMessageDto(sb.toString(),commandMessage));
        return SUCCESS;

    }
}
