package com.example.my_bot.command.commands.role;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.dto.RoleDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.member.AssignMemberResult;
import com.example.my_bot.entity.RoleEntity;
import com.example.my_bot.exception.member.MemberException;
import com.example.my_bot.exception.role.RoleException;
import com.example.my_bot.service.MemberService;
import com.example.my_bot.service.RoleService;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.Optional;

import static com.example.my_bot.constant.MessageConstant.*;
import static com.example.my_bot.enumeration.DefaultRole.ADMINISTRATOR;
import static com.example.my_bot.enumeration.DefaultRole.SENIOR_ADMINISTRATOR;
import static com.example.my_bot.utils.VkChatUtils.*;

@Slf4j
@Command(mainCommandName = "назначить", alternativeCommandNames = {"выдатьроль", "датьроль"}, defaultRole = ADMINISTRATOR, eventable = true)
@RequiredArgsConstructor
public class RoleAssignCommand implements ChatCommand {

    private final VkChatClient vkChatClient;

    private final MemberService memberService;




    @Override
    public void execute(CommandMessageDto cmd) throws ClientException, ApiException {

        long chatId = cmd.getChatId();
        String[] args = cmd.getFirstRowArguments();

        if(args.length==0){
            vkChatClient.sendText(chatId, NOT_ENOUGH_ARGUMENTS_MESSAGE, true);
            return;
        }if(isNumber(args[0])&&!isValidInteger(args[0])){
            vkChatClient.sendText(chatId, NOT_VALID_INTEGER_MESSAGE, true);
            return;
        }
        long userToAssign;
        if(args.length==2){
            Optional<Long> optionalMember = memberService.getCachedMemberIdByUserInput(args[1]);
            if(optionalMember.isEmpty()){
                vkChatClient.sendText(chatId, MEMBER_LINK_IS_NOT_CORRECT, true);
                return;
            } userToAssign = optionalMember.get();
        }else{
            if(cmd.hasReplyMessage()){
                userToAssign=cmd.getReplyMessageFromId().get();
            }else if(cmd.hasFwdMessages()){
                userToAssign=cmd.getFwdMessageFromIds().get(0);
            }else{
                vkChatClient.sendText(chatId, MEMBER_ARGUMENT_ABSENTS, true);
                return;
            }
        }
        AssignMemberResult assignResult;
        try{
         if(isNumber(args[0])){
            assignResult= memberService.assignNewRoleToMember(chatId, userToAssign,Integer.parseInt(args[0]), cmd.getFromId());
         }else{
            assignResult = memberService.assignNewRoleToMember(chatId, userToAssign,args[0], cmd.getFromId());
         }
        }catch(MemberException | RoleException e){
            vkChatClient.sendText(chatId, e.getMessage(), true);
            return;
        }
        if(assignResult!=null){
            RoleDto oldRole = assignResult.getPreviousRole();
            RoleDto newRole = assignResult.getNewRole();
            vkChatClient.sendText(chatId, String.format("✅Роль %s(участника) изменена: «%s»(%d) ➜ «%s»(%d)",
                    createMention(userToAssign),oldRole.getRoleName(), oldRole.getRolePriority(), newRole.getRoleName(), newRole.getRolePriority()), false);

        }

    }






}
