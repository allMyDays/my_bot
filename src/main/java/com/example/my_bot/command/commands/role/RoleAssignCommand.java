package com.example.my_bot.command.commands.role;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.RoleDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.member.AssignMemberResult;
import com.example.my_bot.enumeration.user.NameCase;
import com.example.my_bot.exception.command.CommandException;
import com.example.my_bot.exception.member.MemberException;
import com.example.my_bot.exception.role.RoleException;
import com.example.my_bot.service.MemberService;
import com.example.my_bot.service.UserService;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

import static com.example.my_bot.constant.MessageConstant.*;
import static com.example.my_bot.enumeration.DefaultRole.ADMINISTRATOR;
import static com.example.my_bot.utils.ChatUtils.*;

@Slf4j
@Command(mainCommandName = "назначить", alternativeCommandNames = {"выдатьроль", "датьроль"}, defaultRole = ADMINISTRATOR, eventable = true)
@RequiredArgsConstructor
public class RoleAssignCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(5,60);

    private final VkChatClient vkChatClient;

    private final MemberService memberService;

    private final UserService userService;




    @Override
    public void execute(CommandMessageDto messageDto) throws ClientException, ApiException {

        long chatId = messageDto.getChatId();
        String[] args = messageDto.getFirstRowArguments();
        long peerId = messageDto.getPeerId();

        if(args.length==0){
            vkChatClient.sendText(NOT_ENOUGH_ARGUMENTS_MESSAGE,peerId, true);
            return;
        }if(isNumber(args[0])&&!isValidInteger(args[0])){
            vkChatClient.sendText(NOT_VALID_INTEGER_MESSAGE,peerId, true);
            return;
        }
        long userToAssign;
        if(args.length==2){
            Optional<Long> optionalMember = memberService.getCachedMemberIdByUserInput(args[1]);
            if(optionalMember.isEmpty()){
                vkChatClient.sendText(MEMBER_LINK_IS_NOT_CORRECT,peerId, true);
                return;
            } userToAssign = optionalMember.get();
        }else{
            if(messageDto.hasReplyMessage()){
                userToAssign=messageDto.getReplyMessageFromId().get();
            }else if(messageDto.hasFwdMessages()){
                userToAssign=messageDto.getFwdMessageFromIds().get(0);
            }else{
                vkChatClient.sendText(MEMBER_ARGUMENT_ABSENTS,peerId, true);
                return;
            }
        }
        AssignMemberResult assignResult;
        try{
         if(isNumber(args[0])){
            assignResult= memberService.assignNewRoleToMember(chatId, userToAssign,Integer.parseInt(args[0]), messageDto.getFromId());
         }else{
            assignResult = memberService.assignNewRoleToMember(chatId, userToAssign,args[0], messageDto.getFromId());
         }
        }catch(MemberException | RoleException | CommandException e){
            vkChatClient.sendText(e.getMessage(), peerId,true);
            return;
        }
        if(assignResult!=null){
            RoleDto oldRole = assignResult.getPreviousRole();
            RoleDto newRole = assignResult.getNewRole();

            String username = userService.getUserNameInRequiredCase(userToAssign, NameCase.GENITIVE)
                    .orElse("этого участника");

            vkChatClient.sendText(String.format("✅Роль %s(%s) изменена: «%s»(%d) ➜ «%s»(%d).",
                    createMention(userToAssign),username,oldRole.getRoleName(), oldRole.getRolePriority(), newRole.getRoleName(), newRole.getRolePriority()),
                    peerId,
                    false);

        }

    }


}
