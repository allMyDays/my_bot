package com.example.my_bot.command.commands.stat;


import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.member.inactive.InactiveMemberDto;
import com.example.my_bot.dto.member.inactive.InactiveMembersResult;
import com.example.my_bot.enumeration.CommandExecutionStatus;
import com.example.my_bot.enumeration.TimeZoneType;
import com.example.my_bot.enumeration.user.NameCase;
import com.example.my_bot.exception.member.MemberException;
import com.example.my_bot.exception.message.MessageException;
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
import java.util.*;
import java.util.stream.Collectors;

import static com.example.my_bot.constant.MessageConstant.INVALID_TIME_PERIOD_MESSAGE;
import static com.example.my_bot.enumeration.CommandExecutionStatus.*;
import static com.example.my_bot.enumeration.DefaultRole.MODERATOR;
import static com.example.my_bot.enumeration.chat.AdminChatCommandExecutionMode.ONLY_SINGLE_BOUND_CHAT_AT_ONCE;
import static com.example.my_bot.utils.TextUtils.createMention;
import static com.example.my_bot.utils.TimeUtils.*;

@Slf4j
@RequiredArgsConstructor
@Command(mainCommandName = "неактив", alternativeCommandNames = {"inactive"}, defaultRole = MODERATOR, eventable = true, adminChatCommandExecutionMode = ONLY_SINGLE_BOUND_CHAT_AT_ONCE)
public class ShowInactiveCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(3,60*15);

    private VkChatClient vkChatClient;

    private final GlobalUserService globalUserService;

    private final ChatService chatService;

    private final MessageLogService messageLogService;

    private final MessageMapper messageMapper;

    private final static long DEFAULT_INACTIVE_PERIOD_SEC = 86_400;

    @Autowired
    @Lazy
    public void setVkChatClient(VkChatClient vkChatClient) {
        this.vkChatClient = vkChatClient;
    }


    @Override
    public CommandExecutionStatus execute(CommandMessageDto commandMessage) throws ClientException, ApiException {

        long chatId = commandMessage.getCommandRoutingData().getDataBaseChatId();
        String[] args = commandMessage.getFirstRowArguments();

        SendMessageDto sendMessage = messageMapper.toSendMessageDto(commandMessage);

        Optional<Long> optionalPeriodSec;

        if(args.length<2){
            optionalPeriodSec = Optional.of(DEFAULT_INACTIVE_PERIOD_SEC);
        }
        else{
            optionalPeriodSec = TimeUtils.toSecondsFromString(args[0], args[1]);
        }

        if(optionalPeriodSec.isEmpty()){
            sendMessage.setText(INVALID_TIME_PERIOD_MESSAGE);
            vkChatClient.sendText(sendMessage);
            return ARGUMENT_VALIDATION_ERROR;
        }
        InactiveMembersResult membersResult;

        try{
            membersResult= messageLogService.findCurrentInactiveChatMembers(chatId, optionalPeriodSec.get(), true, null,null);
        }
        catch(MemberException | MessageException e){
            sendMessage.setText(e.getMessage());
            vkChatClient.sendText(sendMessage);
            return BUSINESS_LOGIC_ERROR;
        }

        TimeZoneType chatTimeZone = chatService.getChatTimeZone(chatId);
        Instant now = Instant.now();

        StringBuilder sb = new StringBuilder(
                "&#128270; Было найдено %d участников, которые не писали сообщения %s и более (то есть после %s)\nУчастники и время их последнего сообщения по %s:\n\n"
                        .formatted(
                                membersResult.getInactiveMembers().size(),
                                formatDurationFromSeconds(optionalPeriodSec.get(),false),
                                getFormattedStringDateTimeWithTimeZone(membersResult.getThresholdDate(), chatTimeZone),
                                chatTimeZone.getStringType()
                        )
        );

        Map<Long, String> memberNamesMap = globalUserService.getUserFullNamesInRequiredCase(
                membersResult.getInactiveMembers().stream()
                        .map(InactiveMemberDto::getUserId)
                        .collect(Collectors.toSet()),
                NameCase.NOMINATIVE
        );
        int counter=0;
        for(InactiveMemberDto memberResult: membersResult.getInactiveMembers()){
            Optional<Instant> lastMessage = memberResult.getLastMessageAt();
            sb.append("%d. ".formatted(++counter))
                    .append(createMention(memberResult.getUserId()))
                    .append("(%s)".formatted(memberNamesMap.get(memberResult.getUserId())))
                    .append(" — ");

            if(lastMessage.isEmpty()){
                sb.append("ни одного сообщения за срок.");
            }else{
                sb.append(" %s ".formatted(formatDurationFromSeconds(Duration.between(lastMessage.get(), now).getSeconds(),false)));
                sb.append(" [%s]".formatted(getFormattedStringDateTime(lastMessage.get(), chatTimeZone)));
            }
            sb.append("\n");
        }

        sendMessage.setText(sb.toString());
        vkChatClient.sendText(sendMessage);
        return SUCCESS;

    }
}
