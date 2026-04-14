package com.example.my_bot.command.commands.kick;


import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.member.ParseMemberInputResult;
import com.example.my_bot.exception.command.CannotApplyThisCommandToChatAdminException;
import com.example.my_bot.exception.command.CannotApplyThisCommandToYourselfException;
import com.example.my_bot.exception.member.MemberAccessDeniedException;
import com.example.my_bot.resolver.MemberInputResolver;
import com.example.my_bot.service.MemberService;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import java.util.Optional;

import static com.example.my_bot.constant.MessageConstant.*;
import static com.example.my_bot.enumeration.DefaultRole.SENIOR_MODERATOR;

@Slf4j
@RequiredArgsConstructor
@Command(mainCommandName = "кикнуть", alternativeCommandNames = {"kick", "исключить", "кик"}, defaultRole = SENIOR_MODERATOR, eventable = true)
public class KickCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(5,60);

    private VkChatClient vkChatClient;

    private final MemberService memberService;

    private final MemberInputResolver memberInputResolver;

    @Autowired
    @Lazy
    public void setVkChatClient(VkChatClient vkChatClient) {
        this.vkChatClient = vkChatClient;
    }



    @Override
    public void execute(CommandMessageDto messageDto) throws ClientException, ApiException {

        long chatId = messageDto.getChatId();
        long peerId = messageDto.getPeerId();

        long memberToRemove;

        ParseMemberInputResult inputResult = memberInputResolver.getMemberIdByAnyInput(messageDto, 0);
        if(inputResult.getMemberId().isPresent()){
            memberToRemove = inputResult.getMemberId().get();
        }else{
            vkChatClient.sendText(MEMBER_ARGUMENT_ABSENTS, peerId,true);
            return;
        }

        if(memberToRemove==messageDto.getFromId()){
            vkChatClient.sendText(new CannotApplyThisCommandToYourselfException().getMessage(), peerId,true);
            return;
        }

        try{
            memberService.checkMemberInteractionAbility(chatId, messageDto.getFromId(), memberToRemove);
        }catch (MemberAccessDeniedException e){
            vkChatClient.sendText(e.getMessage(),peerId, true);
            return;
        }


       try{
           vkChatClient.kickOneChatMember(chatId, memberToRemove);
       }catch (ApiException e){
           vkChatClient.sendText("Не удалось исключить пользователя. "+e.getMessage(),peerId, true);
       }

    }
}
