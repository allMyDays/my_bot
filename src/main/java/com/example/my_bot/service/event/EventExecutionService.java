package com.example.my_bot.service.event;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.CommandDispatcher;
import com.example.my_bot.dto.event.EventDto;
import com.example.my_bot.enumeration.event.ChatEventType;
import com.example.my_bot.enumeration.event.MyEventType;
import com.example.my_bot.mapper.CommandMapper;
import com.example.my_bot.service.BanService;
import com.example.my_bot.service.MemberService;
import com.example.my_bot.utils.ChatUtils;
import com.example.my_bot.vk.VkAction;
import com.example.my_bot.vk.VkMessage;
import com.example.my_bot.vk.enumeration.VkActionType;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
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

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.example.my_bot.enumeration.event.EventArgumentType.INTEGER;
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

   private static final Pattern VK_CHAT_INVITE_LINK_PATTERN = Pattern.compile(
            "(?i)(?:https?://)?vk\\.me/join/[A-Za-z0-9_/=-]+"
    );
   private static final Cache<String, Pattern> STRICT_WORD_FILTER_PATTERN_CACHE = Caffeine.newBuilder()
           .expireAfterAccess(15, TimeUnit.MINUTES)
           .build();

    private static final Cache<String, Pattern> REGEX_PATTERN_CACHE = Caffeine.newBuilder()
            .expireAfterAccess(15, TimeUnit.MINUTES)
            .build();



    @Autowired
    @Lazy
    public void setCommandDispatcher(CommandDispatcher commandDispatcher) {
        this.commandDispatcher = commandDispatcher;
    }



    public void executeRequiredChatEvents(VkMessage message){
        long chatId = ChatUtils.extractConversationId(message.getPeerId());
        long fromId = message.getFromId();
        int callerRole = memberService.getMemberRolePriority(chatId, fromId);
        ImmutableMap<ChatEventType, ImmutableSet<EventDto>> eventsCache = eventService.getEventsCache(chatId);

        VkAction action = message.getAction();
        List<MessageAttachment> attachments = message.getAttachments();
        String userText = Optional.ofNullable(message.getText()).orElse("").trim();
        List<ForeignMessage> fwMessages = message.getFwdMessages();

        Map<MessageAttachmentType, List<MessageAttachment>> attachmentMap=Collections.emptyMap();

        ImmutableSet<EventDto> requiredEvents;

        for(ChatEventType chatEventType: ChatEventType.values()){
            switch (chatEventType){
                case ACTION -> {
                    if(action==null) continue;
                }case TEXT -> {
                    if(userText.isEmpty()) continue;
                }case ATTACHMENTS -> {
                    if(attachments.isEmpty()) continue;
                    attachmentMap = attachments
                            .stream()
                            .collect(Collectors.groupingBy(MessageAttachment::getType));
                }case FWD_MESSAGES -> {
                    if(fwMessages.isEmpty()) continue;
                }
            }
            requiredEvents = eventsCache.get(chatEventType);
            if(requiredEvents!=null){
                for(EventDto currentEvent: requiredEvents){
                    if(callerRole>currentEvent.getRolePriority()){
                        continue;
                    }
                    switch (currentEvent.getType()){
                        case ANY_MESSAGE -> {
                            if(action==null){
                                executeEvent(chatId, fromId, null, currentEvent);
                            } continue;
                        }case FWD_QUANTITY -> {
                            int fwdQuantity = countForwardedMessages(message.getFwdMessages());
                            if(fwdQuantity>=Integer.parseInt(currentEvent.getArgument())){
                                executeEvent(chatId, fromId, null, currentEvent);
                            } continue;
                        }
                    }
                    switch (chatEventType){
                        case ACTION -> {
                            handleActionEvent(currentEvent, action, fromId, chatId);
                        }case TEXT -> {
                            handleTextEvent(currentEvent, userText, attachments.size(), fromId, chatId, message.getExpireTTL()!=null);
                        }case ATTACHMENTS -> {
                            handleAttachmentEvent(currentEvent, attachments, attachmentMap, fromId, chatId);
                        }
                    }
                }
            }
            if(chatEventType==ChatEventType.ACTION){
                return; // если текущее событие - action, то не может быть text, attachment

              }
        }
    }

     private void handleActionEvent(@NonNull EventDto eventDto,@NonNull VkAction action, long fromId, long chatId){
        MyEventType eventType = eventDto.getType();
        VkActionType actionType = action.getType();
         Optional<Set<VkActionType>> actionsToExecuteEvent = eventType.getVkActionTypeSet();
         if(actionsToExecuteEvent.isEmpty()){
             log.warn("action event {} returned empty Optional<Set<MessageActionStatus>>.",eventType);
             return;
         }
         if(!actionsToExecuteEvent.get().contains(actionType)){
             return;
         }
         Long memberId = action.getMemberId();
         boolean selfAction = Objects.equals(fromId, memberId);
         switch (eventType){
             case INVITE_ANOTHER, KICK_ANOTHER -> {
                 if(selfAction) return;
             }
             case INVITE_BANNED -> {
                 if(selfAction||!banService.getMemberBanStatus(chatId, memberId).isBanned()) return;
             }
             case INVITE_GROUP -> {
                 if(selfAction||!ChatUtils.isGroupId(memberId)) return;
             }
             case SELF_LEAVE, SELF_RETURN -> {
                 if(!selfAction) return;
             }
         } executeEvent(chatId, fromId, memberId, eventDto);

     }

    private void handleTextEvent(@NonNull EventDto eventDto, @NonNull String userText, int attachmentsSize, long fromId, long chatId, boolean isSelfDestructing){
        MyEventType eventType = eventDto.getType();
        String argument = eventDto.getArgument();

        switch (eventType){
            case WORD_FILTER -> {
                if(!userText.toLowerCase().contains(argument.toLowerCase())) return;

            }case STRICT_WORD_FILTER -> {
                Pattern p = STRICT_WORD_FILTER_PATTERN_CACHE.get(argument,
                        arg-> Pattern.compile("(?<!\\p{L}|\\p{N})" + Pattern.quote(arg) + "(?!\\p{L}|\\p{N})", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS)
                );
                if(!p.matcher(userText).find()) return;

            }case MINIMUM_SYMBOLS -> {
                if(attachmentsSize>0||userText.length()>Integer.parseInt(argument)) return;

            }case MAXIMUM_SYMBOLS -> {
                if(userText.length()<Integer.parseInt(argument)) return;

            }case EMOJI_QUANTITY -> {
                if(EmojiParser.extractEmojis(userText).size()<Integer.parseInt(argument)) return;

            }case ROW_QUANTITY -> {
                int rows = 1;
                for (int i = 0; i < userText.length(); i++) {
                    if(userText.charAt(i) == '\n') rows++;
                }
                if(rows<Integer.parseInt(argument)) return;
            }
            case ALL_MENTION -> {
                if(!PUSH_ALL_PATTERN.matcher(userText).find()) return;
            }
            case ONLINE_MENTION -> {
                if(!PUSH_ONLINE_PATTERN.matcher(userText).find()) return;
            }
            case ANY_LINK -> {
                if (!URL_PATTERN.matcher(userText).find()) return;
            }
            case ZALGO -> {
                if (!isZalgo(userText)) return;
            }
            case CHAT_INVITE_LINK -> {
                if (!VK_CHAT_INVITE_LINK_PATTERN.matcher(userText).find()) return;
            }
            case CAPS -> {
                if (!isMostlyCaps(userText)) return;
            }
            case REGEX_FILTER -> {
                Pattern p = REGEX_PATTERN_CACHE.get(argument, arg ->
                        Pattern.compile(arg, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS));
                if(!p.matcher(userText).find()) return;
            }
            case SELF_DESTRUCTING_MESSAGE -> {
                if (!isSelfDestructing) return;
            }

        } executeEvent(chatId, fromId, null, eventDto);


    }
    private void handleAttachmentEvent(@NonNull EventDto eventDto, @NonNull List <MessageAttachment> attachmentList, @NonNull Map<MessageAttachmentType, List<MessageAttachment>> attachmentMap, long fromId, long chatId){

        MyEventType eventType = eventDto.getType();

        String argument = eventDto.getArgument();
        Integer intArg = eventType.getArgumentType() == INTEGER?Integer.parseInt(argument):null;

        MessageAttachmentType vkAttachmentType = eventType.getVkAttachmentType().orElse(null);

        List<MessageAttachment> currentTypeAttachments;
        if (eventType==MyEventType.ATTACHMENT_QUANTITY){
            currentTypeAttachments = attachmentList;
        } else{
            if(vkAttachmentType==null){
                log.warn("attachment event {} returned empty Optional<MessageAttachmentType>", eventType);
                return;
            }
            currentTypeAttachments = attachmentMap.get(vkAttachmentType);
        }


        if(currentTypeAttachments==null||currentTypeAttachments.isEmpty()){
            return;  // в сообщении вообще нет вложений искомого типа
        }


        switch (eventType){
            case LONG_VOICE_MESSAGE, SHORT_VOICE_MESSAGE -> {
                AudioMessage voiceMessage = currentTypeAttachments.get(0).getAudioMessage(); // голосовое всегда одно, других вложений быть не может
                if (voiceMessage==null||intArg==null) return;
                int duration = voiceMessage.getDuration();
                if(eventType==MyEventType.SHORT_VOICE_MESSAGE) {
                    if(duration>=intArg) return;
                }else{
                    if(duration<=intArg) return;
                }
                executeEvent(chatId, fromId, null, eventDto);
                return;
            }
        }

        if(intArg!=null){
            if(currentTypeAttachments.size()<intArg) {
                return;   // вложения искомого типа есть, но их недостаточно
            }
        }
        executeEvent(chatId, fromId, null, eventDto);
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


    private static boolean isZalgo(@NonNull String text) {
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
    private static boolean isMostlyCaps(@NonNull String text) {
        text = text.trim();
        if (text.isBlank()) return false;

        int letters = 0;
        int upper = 0;

        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);

            if (Character.isLetter(cp)) {
                letters++;
                if (Character.isUpperCase(cp)) upper++;
            }

            i += Character.charCount(cp);
        }

        if (letters < 4) return false;

        return (double) upper / letters >= 0.8;
    }
    private int countForwardedMessages(List<ForeignMessage> fwMessages) {
        if (fwMessages == null || fwMessages.isEmpty()) {
            return 0;
        }

        int count = 0;

        for (ForeignMessage msg : fwMessages) {
            count++;
            count += countForwardedMessages(msg.getFwdMessages());
        }

        return count;
    }

}
