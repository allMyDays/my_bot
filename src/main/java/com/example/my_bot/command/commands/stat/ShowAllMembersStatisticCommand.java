package com.example.my_bot.command.commands.stat;


import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.member.stat.ChatMembersStatisticResult;
import com.example.my_bot.dto.member.stat.MemberStatisticDto;
import com.example.my_bot.enumeration.TimeZoneType;
import com.example.my_bot.enumeration.user.NameCase;
import com.example.my_bot.exception.member.MemberException;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.service.GlobalUserService;
import com.example.my_bot.service.MessageLogService;
import com.example.my_bot.service.chat.ChatService;
import com.example.my_bot.utils.TimeUtils;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.example.my_bot.constant.MessageConstant.INVALID_TIME_PERIOD_MESSAGE;
import static com.example.my_bot.constant.MessageConstant.NOT_VALID_DATE;
import static com.example.my_bot.enumeration.DefaultRole.MODERATOR;
import static com.example.my_bot.utils.TextUtils.createMention;
import static com.example.my_bot.utils.TimeUtils.*;

@Slf4j
@RequiredArgsConstructor
@Command(mainCommandName = "статистика", alternativeCommandNames = {"statistic","stat","стата"}, defaultRole = MODERATOR, eventable = true)
public class ShowAllMembersStatisticCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(3,60*7);

    private VkChatClient vkChatClient;

    private final GlobalUserService globalUserService;

    private final ChatService chatService;

    private final MessageLogService messageLogService;

    private final MessageMapper messageMapper;

    private final static long DEFAULT_STATISTIC_PERIOD_SEC = 86_400;

    private final static int MEMBERS_LIMIT_AT_ONE_USAGE = 50;

    private final static String DATE_SEPARATOR = "-";

    @Autowired
    @Lazy
    public void setVkChatClient(VkChatClient vkChatClient) {
        this.vkChatClient = vkChatClient;
    }


    @Override
    public void execute(CommandMessageDto messageDto) throws ClientException, ApiException {

        long chatId = messageDto.getChatId();
        String[] args = messageDto.getFirstRowArguments();

        TimeZoneType chatTimeZone = chatService.getChatTimeZone(chatId);
        SendMessageDto sendMessage = messageMapper.toSendMessageDto("", messageDto);

        // варианты:
        // !статистика (покажет за сутки)
        // !статистика 3 часа / 4 дня / 7 месяцев и т.д.
        // !статистика 01.11.2025-09.11.2025
        // !статистика 01.06.2026  (покажет статистику за сутки с нужной даты)

        ChatMembersStatisticResult statResult;

        if(args.length==1){
            // либо [!статистика 01.06.2026] либо [!статистика 01.11.2025-09.11.2025]
            Instant now = Instant.now();
            Instant startInstant;
            Instant endInstant;

            if(args[0].contains(DATE_SEPARATOR)){
                // !статистика 01.11.2025-09.11.2025
                String[] dates = args[0].split(DATE_SEPARATOR);
                if(dates.length<2){
                    sendMessage.setText("Аргумент с двумя датами должен иметь вид 01.11.2025-09.11.2025");
                    vkChatClient.sendText(sendMessage);
                    return;
                }
                Optional<LocalDate> startDate= parseDate(dates[0]);
                Optional<LocalDate> endDate= parseDate(dates[1]);

                if(startDate.isEmpty()||endDate.isEmpty()){
                    sendMessage.setText(NOT_VALID_DATE);
                    vkChatClient.sendText(sendMessage);
                    return;
                }
                startInstant = startDate.get().atStartOfDay(chatTimeZone.getZoneOffset()).toInstant();
                endInstant = endDate.get().atStartOfDay(chatTimeZone.getZoneOffset()).toInstant();
            }else{
                // !статистика 01.06.2026
                Optional<LocalDate> startDate= parseDate(args[0]);

                if(startDate.isEmpty()){
                    sendMessage.setText(NOT_VALID_DATE);
                    vkChatClient.sendText(sendMessage);
                    return;
                }
                startInstant = startDate.get().atStartOfDay(chatTimeZone.getZoneOffset()).toInstant();
                endInstant = startInstant.plus(1, ChronoUnit.DAYS);

                if(endInstant.isAfter(now)){
                    endInstant = now;
                }
            }
            try{
                statResult = messageLogService.getAllChatMembersStatForATimePeriod(chatId, startInstant, endInstant, MEMBERS_LIMIT_AT_ONE_USAGE);
            }catch(MemberException e){
                sendMessage.setText(e.getMessage());
                vkChatClient.sendText(sendMessage);
                return;
            }
            sendStatistic(statResult, sendMessage, chatTimeZone);
            return;
        }

        Optional<Long> optionalPeriodSec;

        if(args.length==0){
            // !статистика
            optionalPeriodSec = Optional.of(DEFAULT_STATISTIC_PERIOD_SEC);
        }else{
            // !статистика 2 часа
            optionalPeriodSec = TimeUtils.toSecondsFromString(args[0], args[1]);
        }
        if(optionalPeriodSec.isEmpty()){
            sendMessage.setText(INVALID_TIME_PERIOD_MESSAGE);
            vkChatClient.sendText(sendMessage);
            return;
        }
        try{
            statResult= messageLogService.getAllChatMembersStatForATimePeriod(chatId, optionalPeriodSec.get(), MEMBERS_LIMIT_AT_ONE_USAGE);
        }catch(MemberException e){
            sendMessage.setText(e.getMessage());
            vkChatClient.sendText(sendMessage);
            return;
        }
        sendStatistic(statResult, sendMessage, chatTimeZone);
    }

    private void sendStatistic(ChatMembersStatisticResult statResult, SendMessageDto sendMessage, TimeZoneType chatTimeZone) throws ClientException, ApiException {

        long periodSec = Duration.between(statResult.getStart(), statResult.getEnd()).toSeconds();

        StringBuilder sb = new StringBuilder(
                "\uD83D\uDCCA Статистика чата за период %s \n с %s до %s\n\n[ ✉ сообщения | 🔣 символы ]:\n"
                        .formatted(
                                formatDurationFromSeconds(periodSec,false),
                                getFormattedStringDateTime(statResult.getStart(), chatTimeZone),
                                getFormattedStringDateTimeWithTimeZone(statResult.getEnd(), chatTimeZone)
                        )
        );

        Map<Long, String> memberNamesMap = globalUserService.getUserNamesInRequiredCase(
                statResult.getMemberStatisticDtoList().stream()
                        .map(MemberStatisticDto::getUserId)
                        .collect(Collectors.toSet()),
                NameCase.NOMINATIVE
        );
        int counter=0;
        for(MemberStatisticDto memberStat: statResult.getMemberStatisticDtoList()){
            sb.append("%d. ".formatted(++counter))
                    .append(createMention(memberStat.getUserId()))
                    .append("(%s)".formatted(memberNamesMap.get(memberStat.getUserId())))
                    .append(" — ")
                    .append("%s | %s".formatted(memberStat.getTotalMessages(), memberStat.getTotalSymbols()))
                    .append("\n");
        }
        sb.append("\nВсего сообщений: ")
                .append(statResult.getTotalMessageQuantity())
                .append("\nВсего символов: ")
                .append(statResult.getTotalSymbolsQuantity());

        if(statResult.getTotalMembersQuantity()>MEMBERS_LIMIT_AT_ONE_USAGE){
            sb.append("\nВсего писало %d участников. Было показано %d самых активных."
                    .formatted(statResult.getTotalMembersQuantity(), MEMBERS_LIMIT_AT_ONE_USAGE)
            );
        }

        sendMessage.setText(sb.toString());
        vkChatClient.sendText(sendMessage);

    }

}
