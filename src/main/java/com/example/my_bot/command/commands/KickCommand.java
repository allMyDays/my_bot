package com.example.my_bot.command.commands;


import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.service.MemberService;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
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

    private VkChatClient vkChatClient;

    private final MemberService memberService;

    @Autowired
    @Lazy
    public void setVkChatClient(VkChatClient vkChatClient) {
        this.vkChatClient = vkChatClient;
    }



    @Override
    public void execute(CommandMessageDto cmd) throws ClientException, ApiException {

        long chatId = cmd.getChatId();

        long memberToRemove;

        if(cmd.getFirstRowArguments().length==0){
            if(cmd.hasReplyMessage()){
                memberToRemove = cmd.getReplyMessageFromId().get();
            }else if(cmd.hasFwdMessages()){
                memberToRemove = cmd.getFwdMessageFromIds().get(0);
            }else{
                vkChatClient.sendText(chatId, MEMBER_ARGUMENT_ABSENTS, true);
                return;
            }
        }else{
            Optional<Long> memberOptional = memberService.getCachedMemberIdByUserInput(cmd.getFirstRowArguments()[0]);

            if(memberOptional.isEmpty()){
                vkChatClient.sendText(chatId, MEMBER_LINK_IS_NOT_CORRECT, true);
                return;
            } memberToRemove = memberOptional.get();
        }

        if(memberToRemove==cmd.getFromId()){
            vkChatClient.sendText(chatId, CANNOT_APPLY_THIS_COMMAND_TO_YOURSELF, true);
            return;
        }


        if(memberService.getCachedMemberRolePriority(chatId, cmd.getFromId())
                <memberService.getCachedMemberRolePriority(chatId, memberToRemove)){
            vkChatClient.sendText(chatId, NOT_ENOUGH_ROLE_TO_INTERACT_WITH_MEMBER, true);
            return;
        }

       try{
           vkChatClient.kickChatMember((int)chatId, memberToRemove);
       }catch (ApiException e){
           vkChatClient.sendText(chatId, "Не удалось исключить пользователя. "+e.getMessage(), true);
       }

    }
}
