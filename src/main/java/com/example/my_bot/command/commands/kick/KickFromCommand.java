package com.example.my_bot.command.commands.kick;


import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.member.ParseMemberInputResult;
import com.example.my_bot.entity.MemberEntity;
import com.example.my_bot.enumeration.CommandExecutionStatus;
import com.example.my_bot.enumeration.DefaultRole;
import com.example.my_bot.enumeration.user.NameCase;
import com.example.my_bot.exception.member.MemberException;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.resolver.UserInputResolver;
import com.example.my_bot.service.GlobalUserService;
import com.example.my_bot.service.MemberService;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;

import java.util.Set;

import static com.example.my_bot.constant.MessageConstant.MEMBER_ARGUMENT_ABSENTS;
import static com.example.my_bot.enumeration.CommandExecutionStatus.*;
import static com.example.my_bot.enumeration.DefaultRole.ADMINISTRATOR;
import static com.example.my_bot.enumeration.DefaultRole.MODERATOR;
import static com.example.my_bot.enumeration.chat.AdminChatCommandExecutionMode.ALL_BOUND_CHATS_AT_ONCE;
import static com.example.my_bot.utils.TextUtils.createMention;

@Slf4j
@RequiredArgsConstructor
@Command(mainCommandName = "кикот", alternativeCommandNames = {"kickfrom"}, defaultRole = ADMINISTRATOR, eventable = true, adminChatCommandExecutionMode = ALL_BOUND_CHATS_AT_ONCE)
public class KickFromCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(3,60*3);

    private VkChatClient vkChatClient;

    private final MemberService memberService;

    private final UserInputResolver userInputResolver;

    private final GlobalUserService globalUserService;

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

        long inviterId;

        SendMessageDto sendMessage = messageMapper.toSendMessageDto(commandMessage);

        ParseMemberInputResult inputResult = userInputResolver.getMemberIdByAnyInput(commandMessage, 0);
        if(inputResult.getMemberId().isPresent()){
            inviterId = inputResult.getMemberId().get();
        }else{
            sendMessage.setText(MEMBER_ARGUMENT_ABSENTS);
            vkChatClient.sendText(sendMessage);
            return ARGUMENT_VALIDATION_ERROR;
        }


        if(inviterId!=commandMessage.getFromId()){
        try{
            memberService.checkMemberInteractionAbility(dataBaseChatId, commandMessage.getFromId(), inviterId,true);
        }
        catch (MemberException e){
            sendMessage.setText(e.getMessage());
            vkChatClient.sendText(sendMessage);
            return BUSINESS_LOGIC_ERROR;
          }
        }

        Page<MemberEntity> allRequiredMembers = memberService.getNotKickedMembersInvitedByAndWithRoleLessThan(dataBaseChatId, inviterId, KICK_MEMBERS_WITH_ROLE_LESS_THAN.getRolePriority(), MEMBERS_LIMIT_AT_ONE_USAGE);

        Set<Long> kickedMembers = vkChatClient.kickManyChatMembers(
                commandMessage.getCommandRoutingData(),
                allRequiredMembers.getContent().stream()
                        .filter(m->!m.isChatAdmin())
                        .map(MemberEntity::getUserId)
                        .toList()
        );

        String memberName = globalUserService.getUserFullNameInRequiredCase(inviterId, NameCase.INSTRUMENTAL);

        sendMessage.setText("✅Было исключено %d из %d участников с ролью ниже чем «%s», которые были приглашены %s(%s)."
                .formatted(kickedMembers.size(), allRequiredMembers.getTotalElements(), KICK_MEMBERS_WITH_ROLE_LESS_THAN.getRoleName(),createMention(inviterId), memberName));

        vkChatClient.sendText(sendMessage);
        return SUCCESS;

    }
}
