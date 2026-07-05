package com.example.my_bot.command.commands.member;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.RoleDto;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.member.AssignMemberResult;
import com.example.my_bot.dto.member.ParseMemberInputResult;
import com.example.my_bot.enumeration.command.CommandExecutionStatus;
import com.example.my_bot.enumeration.user.NameCase;
import com.example.my_bot.exception.command.CommandException;
import com.example.my_bot.exception.member.MemberException;
import com.example.my_bot.exception.role.RoleException;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.resolver.UserInputResolver;
import com.example.my_bot.service.GlobalUserService;
import com.example.my_bot.service.MemberService;
import com.example.my_bot.service.RoleService;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static com.example.my_bot.constant.MessageConstant.MEMBER_ARGUMENT_ABSENTS;
import static com.example.my_bot.constant.MessageConstant.MEMBER_ROLE_HAS_BEEN_CHANGED;
import static com.example.my_bot.enumeration.command.CommandExecutionStatus.*;
import static com.example.my_bot.enumeration.DefaultRole.ADMINISTRATOR;
import static com.example.my_bot.enumeration.chat.AdminChatCommandExecutionMode.ALL_BOUND_CHATS_AT_ONCE;
import static com.example.my_bot.utils.TextUtils.createMention;

@Slf4j
@Command(mainCommandName = "понизить", alternativeCommandNames = {"demote"}, defaultRole = ADMINISTRATOR, eventable = true, adminChatCommandExecutionMode = ALL_BOUND_CHATS_AT_ONCE)
@RequiredArgsConstructor
public class DemoteMemberCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(5,60);

    private final VkChatClient vkChatClient;

    private final MemberService memberService;

    private final RoleService roleService;

    private final UserInputResolver userInputResolver;

    private final GlobalUserService userService;

    private final MessageMapper messageMapper;


    @Override
    public CommandExecutionStatus execute(CommandMessageDto commandMessage) throws ClientException, ApiException {

        long chatId = commandMessage.getCommandRoutingData().getDataBaseChatId();

        SendMessageDto sendMessage = messageMapper.toSendMessageDto(true, commandMessage);

        long userToAssign;
        ParseMemberInputResult parseResult = userInputResolver.getMemberIdByAnyInput(commandMessage,0);

        if(parseResult.getMemberId().isPresent()){
            userToAssign = parseResult.getMemberId().get();
        }
        else{
            sendMessage.setText(MEMBER_ARGUMENT_ABSENTS);
            vkChatClient.sendText(sendMessage);
            return ARGUMENT_VALIDATION_ERROR;
        }

        if(userToAssign==-commandMessage.getCommandRoutingData().getExecutorBot().getGroupId())
            return BUSINESS_LOGIC_ERROR;

        AssignMemberResult assignResult;

        try{
            int targetUserRole = memberService.getMemberRolePriority(chatId, userToAssign);
            RoleDto newRoleToAssign = roleService.findTheNearestLowestRole(chatId, targetUserRole, false);
            assignResult = memberService.assignNewRoleToMember(chatId,userToAssign,newRoleToAssign.getRolePriority(),commandMessage.getFromId());

        }
        catch(RoleException | MemberException | CommandException e){
            sendMessage.setText(e.getMessage());
            vkChatClient.sendText(sendMessage);
            return BUSINESS_LOGIC_ERROR;
        }
        String username = userService.getUserFullNameInRequiredCase(userToAssign, NameCase.GENITIVE);

        sendMessage.setText(
                String.format(MEMBER_ROLE_HAS_BEEN_CHANGED,
                        createMention(userToAssign),username,assignResult.getPreviousRole().getRoleName(), assignResult.getNewRole().getRoleName())
        );

        vkChatClient.sendText(sendMessage);
        return SUCCESS;



    }


}
