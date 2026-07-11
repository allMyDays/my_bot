package com.example.my_bot.command.commands.warn;


import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.member.ParseMemberInputResult;
import com.example.my_bot.enumeration.command.CommandExecutionStatus;
import com.example.my_bot.enumeration.user.NameCase;
import com.example.my_bot.exception.ban.BanException;
import com.example.my_bot.exception.command.CommandException;
import com.example.my_bot.exception.member.MemberException;
import com.example.my_bot.exception.warn.WarnException;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.resolver.UserInputResolver;
import com.example.my_bot.service.BanService;
import com.example.my_bot.service.GlobalUserService;
import com.example.my_bot.service.WarnService;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import static com.example.my_bot.constant.MessageConstant.MEMBER_ARGUMENT_ABSENTS;
import static com.example.my_bot.enumeration.DefaultRole.SENIOR_MODERATOR;
import static com.example.my_bot.enumeration.chat.AdminChatCommandExecutionMode.ALL_BOUND_CHATS_AT_ONCE;
import static com.example.my_bot.enumeration.command.CommandExecutionStatus.*;
import static com.example.my_bot.utils.TextUtils.createMention;

@Slf4j
@RequiredArgsConstructor
@Command(mainCommandName = "снятьпред", alternativeCommandNames = {"распред","unwarn"}, defaultRole = SENIOR_MODERATOR, eventable = true, adminChatCommandExecutionMode = ALL_BOUND_CHATS_AT_ONCE)
public class UnwarnCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(5,60);

    private VkChatClient vkChatClient;

    private final WarnService warnService;

    private final UserInputResolver userInputResolver;

    private final GlobalUserService globalUserService;

    private final MessageMapper messageMapper;

    @Autowired
    @Lazy
    public void setVkChatClient(VkChatClient vkChatClient) {
        this.vkChatClient = vkChatClient;
    }



    @Override
    public CommandExecutionStatus execute(CommandMessageDto commandMessage) throws ClientException, ApiException {

        long dataBaseChatId = commandMessage.getCommandRoutingData().getDataBaseChatId();

        long memberToUnwarn;

        SendMessageDto sendMessage = messageMapper.toSendMessageDto(commandMessage);

        ParseMemberInputResult inputResult = userInputResolver.getMemberIdByAnyInput(commandMessage, 0);
        if(inputResult.getMemberId().isPresent()){
            memberToUnwarn = inputResult.getMemberId().get();
        }
        else{
            sendMessage.setText(MEMBER_ARGUMENT_ABSENTS);
            vkChatClient.sendText(sendMessage);
            return ARGUMENT_VALIDATION_ERROR;
        }

       boolean hasBeenDeleted;

       try{
           hasBeenDeleted = warnService.deleteLastMemberWarn(dataBaseChatId, memberToUnwarn, commandMessage.getFromId());
       }
       catch (WarnException | MemberException | CommandException e){
           sendMessage.setText(e.getMessage());
           vkChatClient.sendText(sendMessage);
           return BUSINESS_LOGIC_ERROR;
       }

       sendMessage.setText(
               (hasBeenDeleted?
                       "✅С %s(%s) было успешно снято последнее выданное предупреждение."
                       :"❗У %s(%s) нет предупреждений.")
                       .formatted(createMention(memberToUnwarn), globalUserService.getUserFullNameInRequiredCase(memberToUnwarn, NameCase.GENITIVE)
       ));

       vkChatClient.sendText(sendMessage);

       return SUCCESS;
    }
}
