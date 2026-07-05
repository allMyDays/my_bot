package com.example.my_bot.command.commands.mute;


import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.member.MemberDto;
import com.example.my_bot.dto.member.ParseMemberInputResult;
import com.example.my_bot.enumeration.command.CommandExecutionStatus;
import com.example.my_bot.enumeration.TimeZoneType;
import com.example.my_bot.enumeration.user.NameCase;
import com.example.my_bot.exception.command.CannotApplyThisCommandToYourselfException;
import com.example.my_bot.exception.member.MemberAccessDeniedException;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.resolver.UserInputResolver;
import com.example.my_bot.service.GlobalUserService;
import com.example.my_bot.service.MemberService;
import com.example.my_bot.service.chat.ChatService;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import java.time.Instant;

import static com.example.my_bot.constant.MessageConstant.*;
import static com.example.my_bot.enumeration.command.CommandExecutionStatus.*;
import static com.example.my_bot.enumeration.DefaultRole.SENIOR_MODERATOR;
import static com.example.my_bot.enumeration.chat.AdminChatCommandExecutionMode.ALL_BOUND_CHATS_AT_ONCE;
import static com.example.my_bot.enumeration.member.MemberPresenceType.IN_CHAT;
import static com.example.my_bot.utils.TextUtils.createMention;
import static com.example.my_bot.utils.TimeUtils.getFormattedStringDateTimeWithTimeZone;
import static com.example.my_bot.utils.TimeUtils.toSecondsFromString;

@Slf4j
@RequiredArgsConstructor
@Command(mainCommandName = "мут", alternativeCommandNames = {"mute"}, defaultRole = SENIOR_MODERATOR, eventable = true, adminChatCommandExecutionMode = ALL_BOUND_CHATS_AT_ONCE)
public class MuteCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(7,60);
    private final MessageMapper messageMapper;
    private VkChatClient vkChatClient;
    private final ChatService chatService;
    private final MemberService memberService;
    private final UserInputResolver userInputResolver;
    private final GlobalUserService globalUserService;

    private final static long MUTE_MAX_TIME_PERIOD_SEC = 2_592_000;

    @Autowired
    @Lazy
    public void setVkChatClient(VkChatClient vkChatClient) {
        this.vkChatClient = vkChatClient;
    }


    @Override
    public CommandExecutionStatus execute(CommandMessageDto commandMessage) throws ClientException, ApiException {

        long dataBaseChatId = commandMessage.getCommandRoutingData().getDataBaseChatId();
        String[] args = commandMessage.getFirstRowArguments();
        long fromId = commandMessage.getFromId();

        long memberToMute;
        String reason=null;
        Long muteTimePeriodSec;

        // варианты:
        // !мут @durov - вечный мут
        // !мут (пересланное смс) - вечный мут
        // !мут @durov 2 часа
        // !мут 2 часа (пересланное смс)

        SendMessageDto sendMessage = messageMapper.toSendMessageDto(commandMessage);

        ParseMemberInputResult inputResult = userInputResolver.getMemberIdByAnyInput(commandMessage, 0);

        if(inputResult.getMemberId().isPresent()){
            memberToMute = inputResult.getMemberId().get();

            if(args.length>=2){
                // мут с временными параметрами
                boolean isFwd = inputResult.isFwdMessage();
                if(!isFwd&&args.length<3){   // указали ссылку на участника, но недостаточно аргументов для периода мута
                    // !мут @durov 2 часа
                    sendMessage.setText(NOT_ENOUGH_ARGUMENTS_MESSAGE);
                    vkChatClient.sendText(sendMessage);
                    return ARGUMENT_VALIDATION_ERROR;
                }
                muteTimePeriodSec = toSecondsFromString(args[isFwd?0:1],args[isFwd?1:2]).orElse(null);  //!мут @durov 2 часа или !мут 2 часа (пересланное смс)
                if(muteTimePeriodSec==null){
                    sendMessage.setText(INVALID_TIME_PERIOD_MESSAGE);
                    vkChatClient.sendText(sendMessage);
                    return ARGUMENT_VALIDATION_ERROR;
                }
            }
            else{
                // вечный мут
                muteTimePeriodSec = null;
            }
        }
        else{
            sendMessage.setText(MEMBER_ARGUMENT_ABSENTS);
            vkChatClient.sendText(sendMessage);
            return ARGUMENT_VALIDATION_ERROR;
        }

        if(memberToMute==-commandMessage.getCommandRoutingData().getExecutorBot().getGroupId())
            return BUSINESS_LOGIC_ERROR;

        if(muteTimePeriodSec!=null&&muteTimePeriodSec>MUTE_MAX_TIME_PERIOD_SEC){
            muteTimePeriodSec = null;  // вечный мут, если срок превышает 1 месяц
        }

        if(memberToMute==commandMessage.getFromId()){
            sendMessage.setText(new CannotApplyThisCommandToYourselfException().getMessage());
            vkChatClient.sendText(sendMessage);
            return CommandExecutionStatus.BUSINESS_LOGIC_ERROR;
        }

        MemberDto member = memberService.getCachedMemberInfo(dataBaseChatId, memberToMute).orElse(null);
        if(member==null||member.getPresenceType()!=IN_CHAT){
            sendMessage.setText("Выдать мут можно только участникам, которые на данный момент находятся в чате.");
            vkChatClient.sendText(sendMessage);
            return CommandExecutionStatus.BUSINESS_LOGIC_ERROR;
        }
        try{
            memberService.checkMemberInteractionAbility(dataBaseChatId, commandMessage.getFromId(), memberToMute,true);
        }
        catch (MemberAccessDeniedException e){
            sendMessage.setText(e.getMessage());
            vkChatClient.sendText(sendMessage);
            return CommandExecutionStatus.BUSINESS_LOGIC_ERROR;
        }
        boolean success = vkChatClient.changeChatMemberRestrictions(
                commandMessage.getCommandRoutingData().getExecutorBot(),
                commandMessage.getCommandRoutingData().getVkApiChatId(),
                memberToMute,
                muteTimePeriodSec==null?null:muteTimePeriodSec.intValue(),
                true
        );
        String userName = globalUserService.getUserFullNameInRequiredCase(memberToMute, NameCase.DATIVE);

        if(!success){
            sendMessage.setText("Не удалось выдать %s(%s) запрет на отправку сообщений.".formatted(createMention(memberToMute), userName));
            vkChatClient.sendText(sendMessage);
            return CommandExecutionStatus.VK_API_ERROR;
        }

        String [] rows = commandMessage.getAllRows();
        if(rows.length>=2) reason = rows[1];


       TimeZoneType chatTimeZone = chatService.getChatTimeZone(dataBaseChatId);
       String message = "✅ %s(%s) было запрещено общаться в чате %s"
               .formatted(
                       createMention(memberToMute),
                       userName,
                       muteTimePeriodSec==null
                               ? "навсегда."
                               : "до "+getFormattedStringDateTimeWithTimeZone(Instant.now().plusSeconds(muteTimePeriodSec), chatTimeZone)
               );

       if(!commandMessage.isEventOrTimerMode()){
           message+="\nМодератор: %s(%s)".formatted(
               createMention(fromId),
               globalUserService.getUserFullNameInRequiredCase(fromId, NameCase.NOMINATIVE)
           );
       }
       if(reason!=null){
           message+="\nПричина: "+reason;
       }

       sendMessage.setText(message);
       vkChatClient.sendText(sendMessage);
       return SUCCESS;

    }
}
