package com.example.my_bot.command.impl.role;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.enumeration.DefaultRole;
import com.example.my_bot.service.MemberService;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import static com.example.my_bot.enumeration.DefaultRole.MEMBER;
import static com.example.my_bot.enumeration.DefaultRole.getRoleNameByPriority;
import static com.example.my_bot.utils.VkChatUtils.extractConversationId;

@Component
@Slf4j
@Command(commands = {"роль", "role", "ктоя"}, defaultRole = MEMBER, eventable = true)
public class RoleShowCommand implements ChatCommand {

    private VkChatClient vkChatClient;

    private MemberService memberService;

    @Autowired
    @Lazy
    public void setVkChatClient(VkChatClient vkChatClient, MemberService memberService) {
        this.vkChatClient = vkChatClient;
        this.memberService = memberService;
    }


    @Override
    public void execute(String message, long chatId, long fromId, String[] args) throws ClientException, ApiException {

        if(args.length==0){
            int priority =  memberService.getUserRolePriority(chatId, fromId);
            vkChatClient.sendText(chatId, "Ваша роль в чате — «%s». Приоритет роли: %d"
                    .formatted(getRoleNameByPriority(priority).orElse("Неизвестная роль"), priority),true);
            return;
        }


    }
}