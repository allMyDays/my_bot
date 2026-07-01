package com.example.my_bot.command.commands.kick;


import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.entity.MemberEntity;
import com.example.my_bot.enumeration.CommandExecutionStatus;
import com.example.my_bot.enumeration.DefaultRole;
import com.example.my_bot.enumeration.TimeZoneType;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.service.MemberService;
import com.example.my_bot.service.chat.ChatService;
import com.example.my_bot.utils.TimeUtils;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static com.example.my_bot.constant.MessageConstant.*;
import static com.example.my_bot.enumeration.CommandExecutionStatus.ARGUMENT_VALIDATION_ERROR;
import static com.example.my_bot.enumeration.CommandExecutionStatus.BUSINESS_LOGIC_ERROR;
import static com.example.my_bot.enumeration.DefaultRole.ADMINISTRATOR;
import static com.example.my_bot.enumeration.DefaultRole.MODERATOR;
import static com.example.my_bot.enumeration.chat.AdminChatCommandExecutionMode.ALL_BOUND_CHATS_AT_ONCE;

@Slf4j
@RequiredArgsConstructor
@Command(mainCommandName = "кикновичков", alternativeCommandNames = {"kicknew"}, defaultRole = ADMINISTRATOR, eventable = true, adminChatCommandExecutionMode = ALL_BOUND_CHATS_AT_ONCE)
public class KickNewCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(3,60*3);

    private VkChatClient vkChatClient;

    private final MemberService memberService;

    private final ChatService chatService;

    private final int MAX_COMMAND_PERIOD_IN_SECONDS = 86_400;

    private final MessageMapper messageMapper;

    private final static DefaultRole KICK_MEMBERS_WITH_ROLE_LESS_THAN = MODERATOR;

    private final static int MEMBERS_LIMIT_AT_ONE_USAGE = 100;

    @Autowired
    @Lazy
    public void setVkChatClient(VkChatClient vkChatClient) {
        this.vkChatClient = vkChatClient;
    }



    @Override
    public CommandExecutionStatus execute(CommandMessageDto commandMessage) throws ClientException, ApiException {

        long dataBaseChatId = commandMessage.getCommandRoutingData().getDataBaseChatId();

        SendMessageDto sendMessage = messageMapper.toSendMessageDto(commandMessage);

        String[] args = commandMessage.getFirstRowArguments();
        if(args.length<2){
            sendMessage.setText(NOT_ENOUGH_ARGUMENTS_MESSAGE);
            vkChatClient.sendText(sendMessage);
            return ARGUMENT_VALIDATION_ERROR;
        }

        Optional<Long> optionalPeriod = TimeUtils.toSecondsFromString(args[0], args[1]);
        if(optionalPeriod.isEmpty()){
            sendMessage.setText(INVALID_TIME_PERIOD_MESSAGE);
            vkChatClient.sendText(sendMessage);
            return ARGUMENT_VALIDATION_ERROR;
        }
        long periodInSeconds = optionalPeriod.get();

        if(periodInSeconds>MAX_COMMAND_PERIOD_IN_SECONDS){
            sendMessage.setText("Максимальный период, за который можно исключить новичков — %s"
                    .formatted(TimeUtils.formatDurationFromSeconds(MAX_COMMAND_PERIOD_IN_SECONDS,false)));
            vkChatClient.sendText(sendMessage);
            return BUSINESS_LOGIC_ERROR;
        }

        Instant kickAfter = Instant.now().minusSeconds(periodInSeconds);

        TimeZoneType chatTimeZone = chatService.getChatTimeZone(dataBaseChatId);

        String dateToShow = TimeUtils.getFormattedStringDateTimeWithTimeZone(kickAfter, chatTimeZone);
        Page<MemberEntity> allRequiredMembers = memberService.getNotKickedNewMembersWithRoleLessThan(dataBaseChatId,kickAfter,KICK_MEMBERS_WITH_ROLE_LESS_THAN.getRolePriority(), MEMBERS_LIMIT_AT_ONE_USAGE);

        Set<Long> kickedCommunities = vkChatClient.kickManyChatMembers(
                commandMessage.getCommandRoutingData(),
                allRequiredMembers.getContent().stream()
                        .filter(m->!m.isChatAdmin())
                        .map(MemberEntity::getUserId)
                        .toList()
        );

        sendMessage.setText("✅Было исключено %d из %d новичков с ролью ниже чем «%s», впервые появившихся в чате после %s"
                .formatted(kickedCommunities.size(), allRequiredMembers.getTotalElements(), KICK_MEMBERS_WITH_ROLE_LESS_THAN.getRoleName(), dateToShow));
        vkChatClient.sendText(sendMessage);
        return BUSINESS_LOGIC_ERROR;

    }
}
