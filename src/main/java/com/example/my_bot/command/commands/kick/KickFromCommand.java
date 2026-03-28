package com.example.my_bot.command.commands.kick;


import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.member.ParseMemberInputResult;
import com.example.my_bot.entity.MemberEntity;
import com.example.my_bot.enumeration.DefaultRole;
import com.example.my_bot.enumeration.user.NameCase;
import com.example.my_bot.exception.member.MemberException;
import com.example.my_bot.resolver.MemberInputResolver;
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

import java.util.Optional;
import java.util.Set;

import static com.example.my_bot.constant.MessageConstant.MEMBER_ARGUMENT_ABSENTS;
import static com.example.my_bot.enumeration.DefaultRole.ADMINISTRATOR;
import static com.example.my_bot.enumeration.DefaultRole.MODERATOR;
import static com.example.my_bot.utils.ChatUtils.createMention;

@Slf4j
@RequiredArgsConstructor
@Command(mainCommandName = "кикот", alternativeCommandNames = {"kickfrom"}, defaultRole = ADMINISTRATOR, eventable = true)
public class KickFromCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(3,60*3);

    private VkChatClient vkChatClient;

    private final MemberService memberService;

    private final MemberInputResolver memberInputResolver;

    private final GlobalUserService globalUserService;

    @Autowired
    @Lazy
    public void setVkChatClient(VkChatClient vkChatClient) {
        this.vkChatClient = vkChatClient;
    }



    @Override
    public void execute(CommandMessageDto messageDto) throws ClientException, ApiException {

        long chatId = messageDto.getChatId();

        long peerId = messageDto.getPeerId();

        DefaultRole requiredRole = MODERATOR;

        long inviterId;

        ParseMemberInputResult inputResult = memberInputResolver.getMemberIdByAnyInput(messageDto, 0);
        if(inputResult.getMemberId().isPresent()){
            inviterId = inputResult.getMemberId().get();
        }else{
            vkChatClient.sendText(MEMBER_ARGUMENT_ABSENTS,peerId, true);
            return;
        }


        if(inviterId!=messageDto.getFromId()){
        try{
            memberService.checkMemberInteractionAbility(chatId, messageDto.getFromId(), inviterId);
        }catch (MemberException e){
            vkChatClient.sendText(e.getMessage(),peerId, true);
            return;
          }
        }

        Page<MemberEntity> allRequiredMembers = memberService.getNotKickedMembersInvitedByAndWithRoleLessThan(chatId, inviterId, requiredRole.getRolePriority(), 100);

        Set<Long> kickedMembers = vkChatClient.kickManyChatMembers(chatId,
                allRequiredMembers.getContent().stream()
                        .filter(m->!m.isChatAdmin())
                        .map(MemberEntity::getUserId)
                        .toList());

        String memberName = globalUserService.getUserNameInRequiredCase(inviterId, NameCase.INSTRUMENTAL)
                .orElse("этим участником");

        vkChatClient.sendText("✅Было исключено %d из %d участников с ролью ниже чем «%s», которые были приглашены %s(%s)."
                .formatted(kickedMembers.size(), allRequiredMembers.getTotalElements(), requiredRole.getRoleName(),createMention(inviterId), memberName), messageDto.getPeerId(), true);





    }
}
