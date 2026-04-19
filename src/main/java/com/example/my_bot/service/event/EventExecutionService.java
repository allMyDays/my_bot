package com.example.my_bot.service.event;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.CommandDispatcher;
import com.example.my_bot.dto.event.EventDto;
import com.example.my_bot.mapper.CommandMapper;
import com.example.my_bot.service.BanService;
import com.example.my_bot.service.MemberService;
import com.example.my_bot.utils.ChatUtils;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.vk.api.sdk.objects.messages.ActionOneOf;
import com.vk.api.sdk.objects.messages.Message;
import com.vk.api.sdk.objects.messages.MessageActionStatus;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.regex.Pattern;

import static com.example.my_bot.utils.ChatUtils.*;


@Slf4j
@Service
@RequiredArgsConstructor
public class EventExecutionService {

   private final EventService eventService;
   private final MemberService memberService;
   private final BanService banService;
   private final CommandMapper commandMapper;
   private final VkChatClient vkChatClient;
   private CommandDispatcher commandDispatcher;

   private final static String USER_PARAMETER = "%user%";
   private final static String MEMBER_ID_PARAMETER = "%member_id%";

   private static final Pattern USER_PATTERN =
            Pattern.compile(USER_PARAMETER, Pattern.CASE_INSENSITIVE);

   private static final Pattern MEMBER_ID_PATTERN =
            Pattern.compile(MEMBER_ID_PARAMETER, Pattern.CASE_INSENSITIVE);


    @Autowired
    @Lazy
    public void setCommandDispatcher(CommandDispatcher commandDispatcher) {
        this.commandDispatcher = commandDispatcher;
    }


   public void executeRequiredChatEvents(Message message){
       long chatId = ChatUtils.extractConversationId(message.getPeerId());
       long fromId = message.getFromId();
       ActionOneOf action = message.getAction();
       int callerRole = memberService.getMemberRolePriority(chatId, fromId);

       if(action!=null){
           MessageActionStatus actionType = action.getType();
           Long memberId = action.getMemberId();
           ImmutableMap<MessageActionStatus, ImmutableSet<EventDto>> map = eventService.getActionEventsCache(chatId);
           ImmutableSet<EventDto> requiredEvents = map.get(actionType);

           if(requiredEvents!=null){
                for(EventDto currentEvent: requiredEvents){
                   if(callerRole>currentEvent.getRolePriority()){
                       continue;
                   }
                   boolean toSelf = Objects.equals(fromId, memberId);
                   switch (currentEvent.getType()){
                       case INVITE_ANOTHER, KICK_ANOTHER -> {
                           if(toSelf) continue;
                       }
                       case INVITE_BANNED -> {
                           if(toSelf||!banService.getMemberBanStatus(chatId, memberId).isBanned()) continue;
                       }
                       case INVITE_GROUP -> {
                           if(toSelf||!ChatUtils.isGroupId(memberId)) continue;
                       }
                       case SELF_LEAVE, SELF_RETURN -> {
                           if(!toSelf) continue;
                       }
                   } executeEvent(chatId, fromId, memberId, currentEvent);
               }
           }
           return;
       }


   }

   private void executeEvent(long chatId, long fromId, @Nullable Long memberId, @NonNull EventDto eventDto){

       String fullCommand = USER_PATTERN
               .matcher(eventDto.getFullCommand())
               .replaceAll(createMemberLink(fromId));

       if (memberId!=null) {
           fullCommand = MEMBER_ID_PATTERN
                   .matcher(fullCommand)
                   .replaceAll(createMemberLink(memberId));
       }
       try{
           commandDispatcher.dispatch(
                   commandMapper.toCommandMessageDto(chatId, eventDto.getCreatorId(), fullCommand, true)
           );
       }catch (Exception e) {
           log.warn("error while execution event {} in chat {}.",eventDto.getId(),chatId, e);
           try {
               String message = "Ваше событие с командой «%s» завершилось с ошибкой. Возможно, вам следует его удалить."
                       .formatted(eventDto.getFullCommand());
               vkChatClient.sendText(message, convertToPeerId(chatId), true);
           } catch (Exception ex) {
               log.warn("chat {}: Failed to send notification about error while execution event {}",chatId,eventDto.getId(), ex);
           }
       }

   }

}
