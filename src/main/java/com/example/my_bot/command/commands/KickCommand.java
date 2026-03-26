package com.example.my_bot.command.commands;


import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.exception.member.MemberAccessDeniedException;
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

        if(messageDto.getFirstRowArguments().length==0){
            if(messageDto.hasReplyMessage()){
                memberToRemove = messageDto.getReplyMessageFromId().get();
            }else if(messageDto.hasFwdMessages()){
                memberToRemove = messageDto.getFwdMessageFromIds().get(0);
            }else{
                vkChatClient.sendText(MEMBER_ARGUMENT_ABSENTS,peerId, true);
                return;
            }
        }else{
            Optional<Long> memberOptional = memberService.getCachedMemberIdByUserInput(messageDto.getFirstRowArguments()[0]);

            if(memberOptional.isEmpty()){
                vkChatClient.sendText(MEMBER_LINK_IS_NOT_CORRECT,peerId, true);
                return;
            } memberToRemove = memberOptional.get();
        }

        if(memberToRemove==messageDto.getFromId()){
            vkChatClient.sendText(CANNOT_APPLY_THIS_COMMAND_TO_YOURSELF, peerId,true);
            return;
        }

        try{
            memberService.checkMemberInteractionAbility(chatId, messageDto.getFromId(), memberToRemove);
        }catch (MemberAccessDeniedException e){
            vkChatClient.sendText(e.getMessage(),peerId, true);
            return;
        }


       try{
           vkChatClient.kickOneChatMember((int)chatId, memberToRemove);
       }catch (ApiException e){
           vkChatClient.sendText("Не удалось исключить пользователя. "+e.getMessage(),peerId, true);
       }

    }
}
