package com.example.my_bot.command.commands.kick;


import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.member.ParseMemberInputResult;
import com.example.my_bot.enumeration.command.CommandExecutionStatus;
import com.example.my_bot.exception.command.CannotApplyThisCommandToYourselfException;
import com.example.my_bot.exception.member.MemberAccessDeniedException;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.resolver.UserInputResolver;
import com.example.my_bot.service.MemberService;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import static com.example.my_bot.constant.MessageConstant.*;
import static com.example.my_bot.enumeration.command.CommandExecutionStatus.*;
import static com.example.my_bot.enumeration.DefaultRole.SENIOR_MODERATOR;
import static com.example.my_bot.enumeration.chat.AdminChatCommandExecutionMode.ALL_BOUND_CHATS_AT_ONCE;

@Slf4j
@RequiredArgsConstructor
@Command(mainCommandName = "кикнуть", alternativeCommandNames = {"kick", "исключить", "кик"}, defaultRole = SENIOR_MODERATOR, eventable = true, adminChatCommandExecutionMode = ALL_BOUND_CHATS_AT_ONCE)
public class KickCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(10,60);

    private VkChatClient vkChatClient;

    private final MemberService memberService;

    private final UserInputResolver userInputResolver;

    private final MessageMapper messageMapper;

    @Autowired
    @Lazy
    public void setVkChatClient(VkChatClient vkChatClient) {
        this.vkChatClient = vkChatClient;
    }



    @Override
    public CommandExecutionStatus execute(CommandMessageDto commandMessage) throws ClientException, ApiException {

        long dataBaseChatId = commandMessage.getCommandRoutingData().getDataBaseChatId();

        SendMessageDto sendMessage = messageMapper.toSendMessageDto(commandMessage);

        long memberToRemove;

        ParseMemberInputResult inputResult = userInputResolver.getMemberIdByAnyInput(commandMessage, 0);
        if(inputResult.getMemberId().isPresent()){
            memberToRemove = inputResult.getMemberId().get();
        }
        else{
            sendMessage.setText(MEMBER_ARGUMENT_ABSENTS);
            vkChatClient.sendText(sendMessage);
            return ARGUMENT_VALIDATION_ERROR;
        }

        if(memberToRemove==-commandMessage.getCommandRoutingData().getExecutorBot().getGroupId())
            return BUSINESS_LOGIC_ERROR;

        if(memberToRemove==commandMessage.getFromId()){
            sendMessage.setText(new CannotApplyThisCommandToYourselfException().getMessage());
            vkChatClient.sendText(sendMessage);
            return BUSINESS_LOGIC_ERROR;
        }
        try{
            memberService.checkMemberInteractionAbility(dataBaseChatId, commandMessage.getFromId(), memberToRemove,true);
        }
        catch (MemberAccessDeniedException e){
            sendMessage.setText(e.getMessage());
            vkChatClient.sendText(sendMessage);
            return BUSINESS_LOGIC_ERROR;
        }

       try{
           vkChatClient.kickOneChatMember(commandMessage.getCommandRoutingData(), memberToRemove);
       }
       catch (ApiException e){
           sendMessage.setText("Не удалось исключить пользователя. "+e.getMessage());
           vkChatClient.sendText(sendMessage);
           return VK_API_ERROR;
       }

        return SUCCESS;
    }
}
