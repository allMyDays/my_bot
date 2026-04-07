package com.example.my_bot.command.commands.member;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.RoleDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.member.AssignMemberResult;
import com.example.my_bot.dto.member.ParseMemberInputResult;
import com.example.my_bot.enumeration.user.NameCase;
import com.example.my_bot.exception.command.CommandException;
import com.example.my_bot.exception.member.MemberException;
import com.example.my_bot.exception.role.RoleException;
import com.example.my_bot.resolver.MemberInputResolver;
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
import static com.example.my_bot.enumeration.DefaultRole.ADMINISTRATOR;
import static com.example.my_bot.utils.ChatUtils.createMention;

@Slf4j
@Command(mainCommandName = "понизить", alternativeCommandNames = {"demote"}, defaultRole = ADMINISTRATOR, eventable = true)
@RequiredArgsConstructor
public class DemoteMemberCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(5,60);

    private final VkChatClient vkChatClient;

    private final MemberService memberService;

    private final RoleService roleService;

    private final MemberInputResolver memberInputResolver;

    private final GlobalUserService userService;


    @Override
    public void execute(CommandMessageDto messageDto) throws ClientException, ApiException {

        long chatId = messageDto.getChatId();
        long peerId = messageDto.getPeerId();

        long userToAssign;
        ParseMemberInputResult parseResult = memberInputResolver.getMemberIdByAnyInput(messageDto,0);
        if(parseResult.getMemberId().isPresent()){
            userToAssign = parseResult.getMemberId().get();
        }else{
            vkChatClient.sendText(MEMBER_ARGUMENT_ABSENTS,peerId, true);
            return;
        }
        AssignMemberResult assignResult;
        try{
            int targetUserRole = memberService.getCachedMemberRolePriority(chatId, userToAssign);
            RoleDto newRoleToAssign = roleService.findTheNearestLowestRole(chatId, targetUserRole, false);
            assignResult = memberService.assignNewRoleToMember(chatId,userToAssign,newRoleToAssign.getRolePriority(),messageDto.getFromId());

        }catch(RoleException | MemberException | CommandException e){
            vkChatClient.sendText(e.getMessage(), peerId,true);
            return;
        }
        if(assignResult!=null){
            String username = userService.getUserNameInRequiredCase(userToAssign, NameCase.GENITIVE)
                    .orElse("этого участника");

            vkChatClient.sendText(String.format(MEMBER_ROLE_HAS_BEEN_CHANGED,
                    createMention(userToAssign),username,assignResult.getPreviousRole().getRoleName(), assignResult.getNewRole().getRoleName()),
                    peerId,
                    false);

        }else{
            log.warn("chat {} error: AssignMemberResult is null in DemoteMemberCommand",chatId);
        }


    }


}
