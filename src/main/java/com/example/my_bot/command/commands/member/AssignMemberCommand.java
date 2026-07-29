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
import com.example.my_bot.service.MemberService;
import com.example.my_bot.service.GlobalUserService;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static com.example.my_bot.constant.MessageConstant.*;
import static com.example.my_bot.enumeration.command.CommandExecutionStatus.*;
import static com.example.my_bot.enumeration.DefaultRole.ADMINISTRATOR;
import static com.example.my_bot.enumeration.chat.AdminChatCommandExecutionMode.ALL_BOUND_CHATS_AT_ONCE;
import static com.example.my_bot.utils.TextUtils.*;

@Slf4j
@Command(mainCommandName = "назначить", alternativeCommandNames = {"выдатьроль", "датьроль"}, defaultRole = ADMINISTRATOR, eventable = true, adminChatCommandExecutionMode = ALL_BOUND_CHATS_AT_ONCE)
@RequiredArgsConstructor
public class AssignMemberCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(5,60);

    private final VkChatClient vkChatClient;

    private final MemberService memberService;

    private final UserInputResolver userInputResolver;

    private final GlobalUserService userService;

    private final MessageMapper messageMapper;



    @Override
    public CommandExecutionStatus execute(CommandMessageDto commandMessage) throws ClientException, ApiException {

        long chatId = commandMessage.getCommandRoutingData().getDataBaseChatId();
        String[] args = commandMessage.getFirstRowArguments();

        SendMessageDto sendMessage = messageMapper.toSendMessageDto(true, commandMessage);

        // варианты:
        // !назначить @durov администратор
        // !назначить администратор (пересланное смс)

        if(args.length==0){
            sendMessage.setText(NOT_ENOUGH_ARGUMENTS_MESSAGE);
            vkChatClient.sendText(sendMessage);
            return ARGUMENT_VALIDATION_ERROR;
        }

        long userToAssign;
        String roleToGive;

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

        if(!parseResult.isFwdMessage()){
            // !назначить @durov администратор
            if(args.length<2){
                sendMessage.setText(NOT_ENOUGH_ARGUMENTS_MESSAGE);
                vkChatClient.sendText(sendMessage);
                return ARGUMENT_VALIDATION_ERROR;
            }
            roleToGive=args[1];
        }
        else{
            // !назначить администратор (пересланное смс)
            roleToGive=args[0];
        }

        if(isNumber(roleToGive)&&!isValidInteger(roleToGive)){
            sendMessage.setText(NOT_VALID_INTEGER_MESSAGE);
            vkChatClient.sendText(sendMessage);
            return ARGUMENT_VALIDATION_ERROR;
        }

        AssignMemberResult assignResult;

        try{
            if(isNumber(roleToGive)){
                assignResult= memberService.assignNewRoleToMember(chatId, userToAssign,Integer.parseInt(roleToGive), commandMessage.getFromId());
            }
            else{
                assignResult = memberService.assignNewRoleToMember(chatId, userToAssign,roleToGive, commandMessage.getFromId());
            }
        }
        catch(MemberException | RoleException | CommandException e){
            sendMessage.setText(e.getMessage());
            vkChatClient.sendText(sendMessage);
            return BUSINESS_LOGIC_ERROR;
        }
        RoleDto oldRole = assignResult.getPreviousRole();
        RoleDto newRole = assignResult.getNewRole();

        String username = userService.getUserFullNameInRequiredCase(userToAssign, NameCase.GENITIVE);

        sendMessage.setText(
                String.format(MEMBER_ROLE_HAS_BEEN_CHANGED,
                        createMention(userToAssign),username,oldRole.getRoleName(), newRole.getRoleName())
        );
        vkChatClient.sendText(sendMessage);
        return SUCCESS;

    }
}
