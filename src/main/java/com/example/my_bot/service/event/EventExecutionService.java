package com.example.my_bot.service.event;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.CommandDispatcher;
import com.example.my_bot.dto.event.EventDto;
import com.example.my_bot.enumeration.event.ChatEventType;
import com.example.my_bot.mapper.CommandMapper;
import com.example.my_bot.service.BanService;
import com.example.my_bot.service.MemberService;
import com.example.my_bot.utils.ChatUtils;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.vdurmont.emoji.*;
import com.vk.api.sdk.objects.messages.*;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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

   private static final Pattern PUSH_ALL_PATTERN =
           Pattern.compile("([*@])all\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);

   private static final Pattern PUSH_ONLINE_PATTERN =
           Pattern.compile("([*@])online\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);

   private static final Pattern URL_PATTERN = Pattern.compile(
           "(?i)(?<!\\S)((?:https?://|www\\.|(?:[a-z0-9-]+\\.)+(?:com|net|org|io|ru|de|uk|xyz|info|biz|app|dev))(?:[^\\s]*)?)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);

    Pattern VK_CHAT_INVITE_LINK_PATTERN = Pattern.compile(
            "(?i)(?:https?://)?vk\\.me/join/[A-Za-z0-9_/=-]+"
    );



    @Autowired
    @Lazy
    public void setCommandDispatcher(CommandDispatcher commandDispatcher) {
        this.commandDispatcher = commandDispatcher;
    }


   public void executeRequiredChatEvents(Message message){
       long chatId = ChatUtils.extractConversationId(message.getPeerId());
       long fromId = message.getFromId();
       ActionOneOf currentAction = message.getAction();
       int callerRole = memberService.getMemberRolePriority(chatId, fromId);

       ImmutableMap<ChatEventType, ImmutableSet<EventDto>> eventsCache = eventService.getEventsCache(chatId);
       ImmutableSet<EventDto> requiredEvents=null;

       if(currentAction!=null){
           MessageActionStatus actionType = currentAction.getType();
           Long memberId = currentAction.getMemberId();
           requiredEvents = eventsCache.get(ChatEventType.ACTION);

           if(requiredEvents!=null){
                for(EventDto currentEvent: requiredEvents){
                   Optional<Set<MessageActionStatus>> actionsToExecuteEvent = currentEvent.getType().getChatActionTypeList();
                    if(actionsToExecuteEvent.isEmpty()){
                        log.warn("action event {} returned empty Optional<Set<MessageActionStatus>>.",currentEvent.getType());
                        continue;
                    }
                    if(!actionsToExecuteEvent.get().contains(actionType)||!isRequiredRole(callerRole,currentEvent.getRolePriority())){
                       continue;
                    }
                   boolean selfAction = Objects.equals(fromId, memberId);
                   switch (currentEvent.getType()){
                       case INVITE_ANOTHER, KICK_ANOTHER -> {
                           if(selfAction) continue;
                       }
                       case INVITE_BANNED -> {
                           if(selfAction||!banService.getMemberBanStatus(chatId, memberId).isBanned()) continue;
                       }
                       case INVITE_GROUP -> {
                           if(selfAction||!ChatUtils.isGroupId(memberId)) continue;
                       }
                       case SELF_LEAVE, SELF_RETURN -> {
                           if(!selfAction) continue;
                       }
                   } executeEvent(chatId, fromId, memberId, currentEvent);
               }
           }
           return;
       }

       List<MessageAttachment> attachments = message.getAttachments();
       String userText = message.getText().trim();
       if(!userText.isEmpty()){
           requiredEvents =  eventsCache.get(ChatEventType.TEXT);
           if(requiredEvents!=null){
               for(EventDto currentEvent: requiredEvents){
                   if(!isRequiredRole(callerRole,currentEvent.getRolePriority())){
                       continue;
                   }
                   String lowerCaseArgument = currentEvent.getArgument();
                   switch (currentEvent.getType()){

                       case WORD_FILTER -> {
                           if(!userText.toLowerCase().contains(lowerCaseArgument)) continue;

                       }case STRICT_WORD_FILTER -> {
                           Pattern p = Pattern.compile("(?<!\\p{L}|\\p{N})" + Pattern.quote(lowerCaseArgument) + "(?!\\p{L}|\\p{N})", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);
                           if(!p.matcher(userText).find()) continue;

                       }case MINIMUM_SYMBOLS -> {
                           if(!attachments.isEmpty()||userText.length()>Integer.parseInt(lowerCaseArgument)) continue;

                       }case MAXIMUM_SYMBOLS -> {
                           if(userText.length()<Integer.parseInt(lowerCaseArgument)) continue;

                       }case EMOJI_QUANTITY -> {
                           if(EmojiParser.extractEmojis(userText).size()<Integer.parseInt(lowerCaseArgument)) continue;

                       }case ROW_QUANTITY -> {
                           if(userText.split("\n").length<Integer.parseInt(lowerCaseArgument)) continue;
                       }
                       case ALL_MENTION -> {
                           if(!PUSH_ALL_PATTERN.matcher(userText).find()) continue;
                       }
                       case ONLINE_MENTION -> {
                           if(!PUSH_ONLINE_PATTERN.matcher(userText).find()) continue;
                       }
                       case ANY_LINK -> {
                           if (!URL_PATTERN.matcher(userText).find()) continue;
                       }
                       case ZALGO -> {
                           if (!isZalgo(userText)) continue;
                       }
                       case CHAT_INVITE_LINK -> {
                           if (!VK_CHAT_INVITE_LINK_PATTERN.matcher(userText).find()) continue;
                       }







                   } executeEvent(chatId, fromId, null, currentEvent);












               }

           }









       }






       for(MessageAttachment attachment: attachments){
           MessageAttachmentType type = attachment.getType();


       }






   }

   private boolean isRequiredRole(int eventRole, int userRole){
        return userRole<=eventRole;
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


    public static boolean isZalgo(@NonNull String text) {
        if (text.isEmpty()) return false;

        int total = 0;
        int marks = 0;
        int maxSequence = 0;
        int currentSequence = 0;

        int[] codePoints = text.codePoints().toArray();

        for (int cp : codePoints) {
            total++;

            int type = Character.getType(cp);
            boolean isCombiningMark = type == Character.NON_SPACING_MARK      // Mn
                    || type == Character.COMBINING_SPACING_MARK // Mc
                    || type == Character.ENCLOSING_MARK;

            if (isCombiningMark) {
                marks++;
                currentSequence++;
                maxSequence = Math.max(maxSequence, currentSequence);
            } else {
                currentSequence = 0;
            }
        }

        double density = (double) marks / total;

        if (maxSequence >= 3) return true;
        if (marks >= 6) return true;
        if (density > 0.15) return true;

        return false;
    }

}
