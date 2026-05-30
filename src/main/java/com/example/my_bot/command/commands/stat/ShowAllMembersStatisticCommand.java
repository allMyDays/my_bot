package com.example.my_bot.command.commands.stat;


import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.member.inactive.InactiveMemberDto;
import com.example.my_bot.dto.member.inactive.InactiveMembersResult;
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
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.example.my_bot.constant.MessageConstant.INVALID_TIME_PERIOD_MESSAGE;
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

    @Autowired
    @Lazy
    public void setVkChatClient(VkChatClient vkChatClient) {
        this.vkChatClient = vkChatClient;
    }


    @Override
    public void execute(CommandMessageDto messageDto) throws ClientException, ApiException {

        long chatId = messageDto.getChatId();
        String[] args = messageDto.getFirstRowArguments();

        SendMessageDto sendMessage = messageMapper.toSendMessageDto("", messageDto);

        Optional<Long> optionalPeriodSec;

        if(args.length<2){
            optionalPeriodSec = Optional.of(DEFAULT_STATISTIC_PERIOD_SEC);
        }else{
            optionalPeriodSec = TimeUtils.toSecondsFromString(args[0], args[1]);
        }
        if(optionalPeriodSec.isEmpty()){
            sendMessage.setText(INVALID_TIME_PERIOD_MESSAGE);
            vkChatClient.sendText(sendMessage);
            return;
        }
        ChatMembersStatisticResult statResult;

        try{
            statResult= messageLogService.getAllMembersStatForRequiredTimePeriod(chatId, optionalPeriodSec.get(), MEMBERS_LIMIT_AT_ONE_USAGE);
        }catch(MemberException e){
            sendMessage.setText(e.getMessage());
            vkChatClient.sendText(sendMessage);
            return;
        }
        TimeZoneType chatTimeZone = chatService.getChatTimeZone(chatId);

        StringBuilder sb = new StringBuilder(
                "\uD83D\uDCCA Статистика чата за период %s  (с %s до %s)\n[ ✉ сообщения | 🔣 символы ]:\n\n"
                        .formatted(
                                formatDurationFromSeconds(optionalPeriodSec.get(),false),
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
