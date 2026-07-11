package com.example.my_bot.command.commands.warn;


import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.event.DataForEventExecution;
import com.example.my_bot.dto.event.ExecuteChatEventsResult;
import com.example.my_bot.dto.member.ParseMemberInputResult;
import com.example.my_bot.dto.warn.CreateWarnResult;
import com.example.my_bot.enumeration.TimeZoneType;
import com.example.my_bot.enumeration.command.CommandExecutionStatus;
import com.example.my_bot.enumeration.user.NameCase;
import com.example.my_bot.exception.command.CommandException;
import com.example.my_bot.exception.member.MemberException;
import com.example.my_bot.exception.warn.WarnException;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.resolver.UserInputResolver;
import com.example.my_bot.service.GlobalUserService;
import com.example.my_bot.service.MemberService;
import com.example.my_bot.service.WarnService;
import com.example.my_bot.service.chat.ChatService;
import com.example.my_bot.service.event.EventExecutionService;
import com.example.my_bot.utils.TimeUtils;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import static com.example.my_bot.constant.MessageConstant.*;
import static com.example.my_bot.enumeration.DefaultRole.SENIOR_MODERATOR;
import static com.example.my_bot.enumeration.chat.AdminChatCommandExecutionMode.ALL_BOUND_CHATS_AT_ONCE;
import static com.example.my_bot.enumeration.command.CommandExecutionStatus.*;
import static com.example.my_bot.utils.TextUtils.createMention;
import static com.example.my_bot.utils.TimeUtils.getFormattedStringDateTimeWithTimeZone;

@Slf4j
@Command(mainCommandName = "предупреждение", alternativeCommandNames = {"пред", "warn"}, defaultRole = SENIOR_MODERATOR, eventable = true, adminChatCommandExecutionMode = ALL_BOUND_CHATS_AT_ONCE)
public class WarnCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(6,60);

    private final MessageMapper messageMapper;
    private final ChatService chatService;
    private final WarnService warnService;
    private final UserInputResolver userInputResolver;
    private final GlobalUserService globalUserService;
    private final EventExecutionService eventExecutionService;
    private final MemberService memberService;
    private final VkChatClient vkChatClient;

    public WarnCommand(MessageMapper messageMapper, ChatService chatService, WarnService warnService, UserInputResolver userInputResolver, GlobalUserService globalUserService, @Lazy EventExecutionService eventExecutionService, MemberService memberService, @Lazy VkChatClient vkChatClient) {
        this.messageMapper = messageMapper;
        this.chatService = chatService;
        this.warnService = warnService;
        this.userInputResolver = userInputResolver;
        this.globalUserService = globalUserService;
        this.eventExecutionService = eventExecutionService;
        this.memberService = memberService;
        this.vkChatClient = vkChatClient;
    }

    @Override
    public CommandExecutionStatus execute(CommandMessageDto commandMessage) throws ClientException, ApiException {

        long dataBaseChatId = commandMessage.getCommandRoutingData().getDataBaseChatId();
        String[] args = commandMessage.getFirstRowArguments();
        long fromId = commandMessage.getFromId();

        long memberToWarn;
        String reason=null;
        Long warnTimePeriodSec;

        // варианты:
        // !пред @durov
        // !пред (пересланное смс)
        // !пред @durov 2 часа
        // !пред 2 часа (пересланное смс)

        SendMessageDto sendMessage = messageMapper.toSendMessageDto(commandMessage);

        ParseMemberInputResult inputResult = userInputResolver.getMemberIdByAnyInput(commandMessage, 0);

        if(inputResult.getMemberId().isPresent()){
            memberToWarn = inputResult.getMemberId().get();

            if(args.length>=2){
                // временный пред
                boolean isFwd = inputResult.isFwdMessage();
                if(!isFwd&&args.length<3){   // указали ссылку на участника, но недостаточно аргументов для периода преда
                    sendMessage.setText(NOT_ENOUGH_ARGUMENTS_MESSAGE);
                    vkChatClient.sendText(sendMessage);
                    return ARGUMENT_VALIDATION_ERROR;
                }
                warnTimePeriodSec = TimeUtils.toSecondsFromString(args[isFwd?0:1],args[isFwd?1:2]).orElse(null);
                //!пред @durov 2 часа или !пред 2 часа (пересланное смс)
                if(warnTimePeriodSec==null){
                    sendMessage.setText(INVALID_TIME_PERIOD_MESSAGE);
                    vkChatClient.sendText(sendMessage);
                    return ARGUMENT_VALIDATION_ERROR;
                }
            }else{
                // вечный пред
                warnTimePeriodSec = chatService.getDefaultWarnTimePeriod(dataBaseChatId).orElse(null);
            }
        }
        else{
            sendMessage.setText(MEMBER_ARGUMENT_ABSENTS);
            vkChatClient.sendText(sendMessage);
            return ARGUMENT_VALIDATION_ERROR;
        }

        if(memberToWarn==-commandMessage.getCommandRoutingData().getExecutorBot().getGroupId()) return BUSINESS_LOGIC_ERROR;

        String [] rows = commandMessage.getAllRows();
        if(rows.length>=2) reason = rows[1];

       String userName = globalUserService.getUserFullNameInRequiredCase(memberToWarn, NameCase.NOMINATIVE);

       CreateWarnResult result;

       try{
           result = warnService.createNewWarn(dataBaseChatId, memberToWarn, reason, warnTimePeriodSec, fromId);
       }
       catch (CommandException | MemberException | WarnException e){
           sendMessage.setText(e.getMessage());
           vkChatClient.sendText(sendMessage);
           return BUSINESS_LOGIC_ERROR;
       }

       if(result.isWarnLimitReached()){
           sendMessage.setText("%s(%s) достиг лимита предупреждений.".formatted(createMention(memberToWarn),userName));
           vkChatClient.sendText(sendMessage);

           ExecuteChatEventsResult executeEventsResult = eventExecutionService.executeRequiredChatEvents(
                   new DataForEventExecution(dataBaseChatId, memberToWarn, commandMessage.getConversationMessageId(), null, null, null, null, null, null, false, commandMessage.getCommandRoutingData(), true)
           );
           if(executeEventsResult.getExecutedEventsCounter()==0){
               // не выполнилось никаких кастомных событий на достижение лимита предупреждений. нужно просто исключить
               if(!memberService.isChatAdmin(dataBaseChatId, memberToWarn)){
                   try{
                       vkChatClient.kickOneChatMember(commandMessage.getCommandRoutingData(), memberToWarn);
                   }catch(Exception e){
                       log.info("chat {}: couldn't remove member {} after warn limit has been reached", dataBaseChatId, memberToWarn);
                       return VK_API_ERROR;
                   }
               }
           }
           return SUCCESS;
       }

       TimeZoneType chatTimeZone = chatService.getChatTimeZone(dataBaseChatId);

       String message = "⚠ %s(%s) получает новое предупреждение: [%d/%d].%s"
               .formatted(
                       createMention(memberToWarn),
                       userName,
                       result.getNewWarnQuantity(),
                       result.getMaxWarnQuantity(),
                       result.getOptionalExpiresAt().map(instant -> "\n⏳ Оно будет снято "+getFormattedStringDateTimeWithTimeZone(instant, chatTimeZone)).orElse("")

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
