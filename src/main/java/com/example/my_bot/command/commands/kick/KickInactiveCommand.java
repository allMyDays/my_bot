package com.example.my_bot.command.commands.kick;


import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.member.inactive.InactiveMemberDto;
import com.example.my_bot.dto.member.inactive.InactiveMembersResult;
import com.example.my_bot.enumeration.CommandExecutionStatus;
import com.example.my_bot.enumeration.DefaultRole;
import com.example.my_bot.enumeration.TimeZoneType;
import com.example.my_bot.exception.member.MemberException;
import com.example.my_bot.exception.message.MessageException;
import com.example.my_bot.mapper.MessageMapper;
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

import java.util.Optional;
import java.util.Set;

import static com.example.my_bot.constant.MessageConstant.INVALID_TIME_PERIOD_MESSAGE;
import static com.example.my_bot.constant.MessageConstant.NOT_ENOUGH_ARGUMENTS_MESSAGE;
import static com.example.my_bot.enumeration.CommandExecutionStatus.*;
import static com.example.my_bot.enumeration.DefaultRole.ADMINISTRATOR;
import static com.example.my_bot.enumeration.DefaultRole.MODERATOR;
import static com.example.my_bot.enumeration.chat.AdminChatCommandExecutionMode.ALL_BOUND_CHATS_AT_ONCE;

@Slf4j
@RequiredArgsConstructor
@Command(mainCommandName = "кикнеактив", alternativeCommandNames = {"kickinactive"}, defaultRole = ADMINISTRATOR, eventable = true, adminChatCommandExecutionMode = ALL_BOUND_CHATS_AT_ONCE)
public class KickInactiveCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(3,15);

    private VkChatClient vkChatClient;

    private final ChatService chatService;

    private final MessageMapper messageMapper;

    private final MessageLogService messageLogService;

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
        String[] args = commandMessage.getFirstRowArguments();

        SendMessageDto sendMessage = messageMapper.toSendMessageDto(commandMessage);

        if(args.length<2){
            sendMessage.setText(NOT_ENOUGH_ARGUMENTS_MESSAGE);
            vkChatClient.sendText(sendMessage);
            return ARGUMENT_VALIDATION_ERROR;
        }
        Optional<Long> optionalPeriodSec = TimeUtils.toSecondsFromString(args[0], args[1]);
        if(optionalPeriodSec.isEmpty()){
            sendMessage.setText(INVALID_TIME_PERIOD_MESSAGE);
            vkChatClient.sendText(sendMessage);
            return ARGUMENT_VALIDATION_ERROR;
        }
        long periodSec = optionalPeriodSec.get();

        InactiveMembersResult membersResult;

        try{
            membersResult= messageLogService.findCurrentInactiveChatMembers(dataBaseChatId, periodSec,false, KICK_MEMBERS_WITH_ROLE_LESS_THAN.getRolePriority(),MEMBERS_LIMIT_AT_ONE_USAGE);
        }
        catch(MemberException | MessageException e){
            sendMessage.setText(e.getMessage());
            vkChatClient.sendText(sendMessage);
            return BUSINESS_LOGIC_ERROR;
        }
        TimeZoneType chatTimeZone = chatService.getChatTimeZone(dataBaseChatId);

        String dateToShow = TimeUtils.getFormattedStringDateTimeWithTimeZone(membersResult.getThresholdDate(), chatTimeZone);

        Set<Long> kickedMembers = vkChatClient.kickManyChatMembers(
                commandMessage.getCommandRoutingData(),
                membersResult.getInactiveMembers().stream()
                        .map(InactiveMemberDto::getUserId)
                        .toList()
        );

        sendMessage.setText("✅Было исключено %d из %d участников с ролью ниже чем «%s», которые не писали сообщения после %s"
                .formatted(kickedMembers.size(), membersResult.getTotalInactiveQuantity(), KICK_MEMBERS_WITH_ROLE_LESS_THAN.getRoleName(), dateToShow));
        vkChatClient.sendText(sendMessage);
        return SUCCESS;

    }
}
