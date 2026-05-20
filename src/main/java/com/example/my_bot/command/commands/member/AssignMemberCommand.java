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
import static com.example.my_bot.enumeration.DefaultRole.ADMINISTRATOR;
import static com.example.my_bot.utils.TextUtils.*;

@Slf4j
@Command(mainCommandName = "назначить", alternativeCommandNames = {"выдатьроль", "датьроль"}, defaultRole = ADMINISTRATOR, eventable = true)
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
    public void execute(CommandMessageDto messageDto) throws ClientException, ApiException {

        long chatId = messageDto.getChatId();
        String[] args = messageDto.getFirstRowArguments();

        SendMessageDto sendMessage = messageMapper.toSendMessageDto("",true, messageDto);

        // варианты:
        // !назначить @durov администратор
        // !назначить администратор (пересланное смс)

        if(args.length==0){
            sendMessage.setText(NOT_ENOUGH_ARGUMENTS_MESSAGE);
            vkChatClient.sendText(sendMessage);
            return;
        }
        long userToAssign;
        String roleToGive;

        ParseMemberInputResult parseResult = userInputResolver.getMemberIdByAnyInput(messageDto,0);
        if(parseResult.getMemberId().isPresent()){
            userToAssign = parseResult.getMemberId().get();
        }else{
            sendMessage.setText(MEMBER_ARGUMENT_ABSENTS);
            vkChatClient.sendText(sendMessage);
            return;
        }
        if(!parseResult.isFwdMessage()){
            // !назначить @durov администратор
            if(args.length<2){
                sendMessage.setText(NOT_ENOUGH_ARGUMENTS_MESSAGE);
                vkChatClient.sendText(sendMessage);
                return;
            } roleToGive=args[1];
        }else{
            // !назначить администратор (пересланное смс)
            roleToGive=args[0];
        }
        if(isNumber(roleToGive)&&!isValidInteger(roleToGive)){
            sendMessage.setText(NOT_VALID_INTEGER_MESSAGE);
            vkChatClient.sendText(sendMessage);
            return;
        }

        AssignMemberResult assignResult;
        try{
         if(isNumber(roleToGive)){
            assignResult= memberService.assignNewRoleToMember(chatId, userToAssign,Integer.parseInt(roleToGive), messageDto.getFromId());
         }else{
            assignResult = memberService.assignNewRoleToMember(chatId, userToAssign,roleToGive, messageDto.getFromId());
         }
        }catch(MemberException | RoleException | CommandException e){
            sendMessage.setText(e.getMessage());
            vkChatClient.sendText(sendMessage);
            return;
        }
        if(assignResult!=null){
            RoleDto oldRole = assignResult.getPreviousRole();
            RoleDto newRole = assignResult.getNewRole();

            String username = userService.getUserNameInRequiredCase(userToAssign, NameCase.GENITIVE)
                    .orElse("этого участника");

            sendMessage.setText(
                    String.format(MEMBER_ROLE_HAS_BEEN_CHANGED,
                    createMention(userToAssign),username,oldRole.getRoleName(), newRole.getRoleName())
            );
            vkChatClient.sendText(sendMessage);

        }else{
            log.warn("chat {} error: AssignMemberResult is null in AssignMemberCommand",chatId);
        }

    }


}
