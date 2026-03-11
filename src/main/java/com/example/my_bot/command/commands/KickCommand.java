package com.example.my_bot.command.commands;


import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.service.MemberService;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import java.util.Optional;

import static com.example.my_bot.constant.MessageConstant.*;
import static com.example.my_bot.enumeration.DefaultRole.MEMBER;
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
    public void execute(long chatId, long fromId, String[] args) throws ClientException, ApiException {

        if(args.length==0){
            vkChatClient.sendText(chatId, NOT_ENOUGH_ARGUMENTS_MESSAGE, true);
            return;
        }

        Optional<Long> memberIdOpt = memberService.getCachedMemberIdByUserInput(args[0]);

        if(memberIdOpt.isEmpty()){
            vkChatClient.sendText(chatId, MEMBER_LINK_IS_NOT_CORRECT, true);
            return;
        }
        long memberId = memberIdOpt.get();

        if(memberService.getCachedMemberRolePriority(chatId, fromId)
                <memberService.getCachedMemberRolePriority(chatId, memberId)){
            vkChatClient.sendText(chatId, NOT_ENOUGH_ROLE_TO_INTERACT_WITH_MEMBER, true);
            return;
        }

       try{
           vkChatClient.kickChatMember((int)chatId, memberId);
       }catch (ApiException e){
           vkChatClient.sendText(chatId, "Не удалось исключить пользователя. "+e.getMessage(), true);
       }

    }
}
