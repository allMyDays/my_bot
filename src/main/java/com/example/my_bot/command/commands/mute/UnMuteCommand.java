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
import static com.example.my_bot.enumeration.DefaultRole.SENIOR_MODERATOR;
import static com.example.my_bot.enumeration.member.MemberPresenceType.IN_CHAT;
import static com.example.my_bot.utils.TextUtils.createMention;
import static com.example.my_bot.utils.TimeUtils.getFormattedStringDateTimeWithTimeZone;
import static com.example.my_bot.utils.TimeUtils.toSecondsFromString;

@Slf4j
@RequiredArgsConstructor
@Command(mainCommandName = "размут", alternativeCommandNames = {"unmute"}, defaultRole = SENIOR_MODERATOR, eventable = true)
public class UnMuteCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(7,60);
    private final MessageMapper messageMapper;
    private VkChatClient vkChatClient;
    private final ChatService chatService;
    private final MemberService memberService;
    private final UserInputResolver userInputResolver;
    private final GlobalUserService globalUserService;


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

        long memberToUnMute;

        // варианты:
        // !размут @durov
        // !размут (пересланное смс)

        SendMessageDto sendMessage = messageMapper.toSendMessageDto("",messageDto);

        ParseMemberInputResult inputResult = userInputResolver.getMemberIdByAnyInput(messageDto, 0);
        if(inputResult.getMemberId().isPresent()){
            memberToUnMute = inputResult.getMemberId().get();
        }else{
            sendMessage.setText(MEMBER_ARGUMENT_ABSENTS);
            vkChatClient.sendText(sendMessage);
            return;
        }
        if(memberToUnMute==messageDto.getFromId()){
            sendMessage.setText(new CannotApplyThisCommandToYourselfException().getMessage());
            vkChatClient.sendText(sendMessage);
            return;
        }
        MemberDto member = memberService.getCachedMemberInfo(chatId, memberToUnMute).orElse(null);
        if(member==null||member.getPresenceType()!=IN_CHAT){
            sendMessage.setText("Выдать размут можно только участникам, которые на данный момент находятся в чате.");
            vkChatClient.sendText(sendMessage);
            return;
        }
        try{
            memberService.checkMemberInteractionAbility(chatId, messageDto.getFromId(), memberToUnMute,true);
        }catch (MemberAccessDeniedException e){
            sendMessage.setText(e.getMessage());
            vkChatClient.sendText(sendMessage);
            return;
        }
        vkChatClient.changeChatMemberRestrictions(chatId, memberToUnMute, -1, false);

        String userName = globalUserService.getUserFullNameInRequiredCase(memberToUnMute, NameCase.GENITIVE);

       String message = "✅С %s(%s) был снят запрет на отправку сообщений."
               .formatted(
                       createMention(memberToUnMute),
                       userName
               );

       if(!messageDto.isEventOrTimerMode()){
           message += "\nМодератор: %s(%s)".formatted(
                   createMention(fromId),
                   globalUserService.getUserFullNameInRequiredCase(fromId, NameCase.NOMINATIVE)
           );
       }

       sendMessage.setText(message);
       vkChatClient.sendText(sendMessage);

    }
}
