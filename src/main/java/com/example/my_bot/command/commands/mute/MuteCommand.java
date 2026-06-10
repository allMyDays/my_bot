package com.example.my_bot.command.commands.mute;


import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.member.MemberDto;
import com.example.my_bot.dto.member.ParseMemberInputResult;
import com.example.my_bot.enumeration.TimeZoneType;
import com.example.my_bot.enumeration.member.MemberPresenceType;
import com.example.my_bot.enumeration.user.NameCase;
import com.example.my_bot.exception.ban.BanException;
import com.example.my_bot.exception.command.CannotApplyThisCommandToYourselfException;
import com.example.my_bot.exception.command.CommandException;
import com.example.my_bot.exception.member.MemberAccessDeniedException;
import com.example.my_bot.exception.member.MemberException;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.resolver.UserInputResolver;
import com.example.my_bot.service.BanService;
import com.example.my_bot.service.GlobalUserService;
import com.example.my_bot.service.MemberService;
import com.example.my_bot.service.chat.ChatService;
import com.example.my_bot.utils.TimeUtils;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import java.time.Instant;
import java.util.Optional;

import static com.example.my_bot.constant.MessageConstant.*;
import static com.example.my_bot.enumeration.DefaultRole.SENIOR_MODERATOR;
import static com.example.my_bot.enumeration.member.MemberPresenceType.IN_CHAT;
import static com.example.my_bot.utils.TextUtils.createMention;
import static com.example.my_bot.utils.TimeUtils.getFormattedStringDateTimeWithTimeZone;
import static com.example.my_bot.utils.TimeUtils.toSecondsFromString;
import static com.example.my_bot.vk.enumeration.ChatErrorCode.USER_NOT_FOUND_IN_CHAT;

@Slf4j
@RequiredArgsConstructor
@Command(mainCommandName = "мут", alternativeCommandNames = {"mute"}, defaultRole = SENIOR_MODERATOR, eventable = true)
public class MuteCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(7,60);
    private final MessageMapper messageMapper;
    private VkChatClient vkChatClient;
    private final ChatService chatService;
    private final MemberService memberService;
    private final UserInputResolver userInputResolver;
    private final GlobalUserService globalUserService;

    private final static long MUTE_DEFAULT_TIME_PERIOD_SEC = 60*30;
    private final static long MUTE_MAX_TIME_PERIOD_SEC = 2_592_000;

    @Autowired
    @Lazy
    public void setVkChatClient(VkChatClient vkChatClient) {
        this.vkChatClient = vkChatClient;
    }



    @Override
    public void execute(CommandMessageDto messageDto) throws ClientException, ApiException {

        long chatId = messageDto.getChatId();
        String[] args = messageDto.getFirstRowArguments();
        long fromId = messageDto.getFromId();

        long memberToMute;
        String reason=null;
        Long muteTimePeriodSec;

        // варианты:
        // !мут @durov
        // !мут (пересланное смс)
        // !мут @durov 2 часа
        // !мут 2 часа (пересланное смс)

        SendMessageDto sendMessage = messageMapper.toSendMessageDto("",messageDto);

        ParseMemberInputResult inputResult = userInputResolver.getMemberIdByAnyInput(messageDto, 0);
        if(inputResult.getMemberId().isPresent()){
            memberToMute = inputResult.getMemberId().get();
            if(args.length>=2){ // мут с временными параметрами
                boolean isFwd = inputResult.isFwdMessage();
                if(!isFwd&&args.length<3){   // указали ссылку на участника, но недостаточно аргументов для периода мута
                    // !мут @durov 2 часа
                    sendMessage.setText(NOT_ENOUGH_ARGUMENTS_MESSAGE);
                    vkChatClient.sendText(sendMessage);
                    return;
                }
                muteTimePeriodSec = toSecondsFromString(args[isFwd?0:1],args[isFwd?1:2]).orElse(null); //!мут @durov 2 часа или !мут 2 часа (пересланное смс)
                if(muteTimePeriodSec==null){
                    sendMessage.setText(INVALID_TIME_PERIOD_MESSAGE);
                    vkChatClient.sendText(sendMessage);
                    return;
                }
            }else{
                muteTimePeriodSec = MUTE_DEFAULT_TIME_PERIOD_SEC;
            }
        }else{
            sendMessage.setText(MEMBER_ARGUMENT_ABSENTS);
            vkChatClient.sendText(sendMessage);
            return;
        }

        if(muteTimePeriodSec>MUTE_MAX_TIME_PERIOD_SEC){
            sendMessage.setText(
                    "Максимальный период, на который можно выдать мут — "+
                            TimeUtils.formatDurationFromSeconds(MUTE_MAX_TIME_PERIOD_SEC,false)
            );
            vkChatClient.sendText(sendMessage);
            return;
        }
        if(memberToMute==messageDto.getFromId()){
            sendMessage.setText(new CannotApplyThisCommandToYourselfException().getMessage());
            vkChatClient.sendText(sendMessage);
            return;
        }
        MemberDto member = memberService.getCachedMemberInfo(chatId, memberToMute).orElse(null);
        if(member==null||member.getPresenceType()!=IN_CHAT){
            sendMessage.setText("Выдать мут можно только участникам, которые на данный момент находятся в чате.");
            vkChatClient.sendText(sendMessage);
            return;
        }
        try{
            memberService.checkMemberInteractionAbility(chatId, messageDto.getFromId(), memberToMute,true);
        }catch (MemberAccessDeniedException e){
            sendMessage.setText(e.getMessage());
            vkChatClient.sendText(sendMessage);
            return;
        }
        boolean success = vkChatClient.changeChatMemberRestrictions(chatId, memberToMute, muteTimePeriodSec, true);
        String userName = globalUserService.getUserFullNameInRequiredCase(memberToMute, NameCase.DATIVE);
        if(!success){
            sendMessage.setText("Не удалось выдать %s(%s) запрет на отправку сообщений.".formatted(createMention(memberToMute), userName));
            vkChatClient.sendText(sendMessage);
            return;
        }

        String [] rows = messageDto.getAllRows();
        if(rows.length>=2) reason = rows[1];

       Instant mutedUntil= Instant.now().plusSeconds(muteTimePeriodSec);


       TimeZoneType chatTimeZone = chatService.getChatTimeZone(chatId);
       String message = "✅ %s(%s) было запрещено общаться в чате до %s"
               .formatted(
                       createMention(memberToMute),
                       userName,
                       getFormattedStringDateTimeWithTimeZone(mutedUntil, chatTimeZone)
               );

       if(!messageDto.isEventOrTimerMode()){
           message+="\nМодератор: %s(%s)".formatted(
               createMention(fromId),
               globalUserService.getUserFullNameInRequiredCase(fromId, NameCase.NOMINATIVE)
           );
       }if(reason!=null){
           message+="\nПричина: "+reason;
       }

       sendMessage.setText(message);
       vkChatClient.sendText(sendMessage);

    }
}
