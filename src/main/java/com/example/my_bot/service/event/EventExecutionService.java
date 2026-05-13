package com.example.my_bot.service.event;

import com.example.my_bot.cache.key.EventIdAndMemberIdAndUniqueIdKey;
import com.example.my_bot.cache.key.EventIdAndMemberIdKey;
import com.example.my_bot.cache.key.GroupIdAndUserIdKey;
import com.example.my_bot.cache.value.MessageCounter;
import com.example.my_bot.cache.value.TimePeriodAndCallQuantity;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.CommandDispatcher;
import com.example.my_bot.dto.event.EventDto;
import com.example.my_bot.enumeration.TimeZoneType;
import com.example.my_bot.enumeration.event.ChatEventType;
import com.example.my_bot.enumeration.event.MyEventType;
import com.example.my_bot.mapper.CommandMapper;
import com.example.my_bot.service.BanService;
import com.example.my_bot.service.MemberService;
import com.example.my_bot.service.chat.ChatService;
import com.example.my_bot.utils.ChatUtils;
import com.example.my_bot.utils.TextUtils;
import com.example.my_bot.vk.VkAction;
import com.example.my_bot.vk.VkMessage;
import com.example.my_bot.vk.attachment.Video;
import com.example.my_bot.vk.attachment.VkMessageAttachment;
import com.example.my_bot.vk.enumeration.VideoType;
import com.example.my_bot.vk.enumeration.VkActionType;
import com.example.my_bot.vk.enumeration.VkMessageAttachmentType;
import com.github.benmanes.caffeine.cache.*;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.objects.messages.*;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.units.qual.C;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.example.my_bot.enumeration.event.ChatEventType.*;
import static com.example.my_bot.enumeration.event.EventArgumentType.INTEGER;
import static com.example.my_bot.enumeration.event.MyEventType.*;
import static com.example.my_bot.service.event.EventService.getMaxPeriodForAdvancedEvents;
import static com.example.my_bot.utils.ChatUtils.convertToPeerId;
import static com.example.my_bot.utils.TextUtils.*;
import static com.example.my_bot.utils.TextUtils.isMostlyCaps;
import static com.example.my_bot.utils.TextUtils.isZalgo;


@Slf4j
@Service
@RequiredArgsConstructor
public class EventExecutionService {

   private final EventService eventService;
   private final MemberService memberService;
   private final BanService banService;
   private final CommandMapper commandMapper;
   private final VkChatClient vkChatClient;
   private final ChatService chatService;
   private CommandDispatcher commandDispatcher;

   private final static String USER_PARAMETER = "%user%";
   private final static String MEMBER_ID_PARAMETER = "%member_id%";

   private static final Pattern USER_PARAMETER_PATTERN =
            Pattern.compile(USER_PARAMETER, Pattern.CASE_INSENSITIVE);

   private static final Pattern MEMBER_ID_PATTERN =
            Pattern.compile(MEMBER_ID_PARAMETER, Pattern.CASE_INSENSITIVE);

   private static final Pattern PUSH_ALL_PATTERN =
           Pattern.compile("([*@])all\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);

   private static final Pattern PUSH_ONLINE_PATTERN =
           Pattern.compile("([*@])online\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);

   private static final Pattern ANY_PUSH_PATTERN =
            Pattern.compile("\\[[^\\]\\[]+\\|[^\\]\\[]+\\]", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);

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

    private static final Cache<GroupIdAndUserIdKey, Boolean> COMMUNITY_SUBSCRIPTIONS_CACHE = Caffeine.newBuilder()
            .expireAfterWrite(30, TimeUnit.SECONDS)
            .build();

    // кеш вызовов события "одинаковые сообщения": key -> (user text -> quantity)
    private static final Cache<EventIdAndMemberIdKey, MessageCounter> SAME_MESSAGES_CACHE = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build();

    // хранение подсчёта вызовов конкретного события для конкретного участника чата: (eventId + memberId) -> AtomicInteger
    private final static Cache<EventIdAndMemberIdKey, AtomicInteger> advancedEventCallCounters = Caffeine.newBuilder()
            .expireAfterWrite(getMaxPeriodForAdvancedEvents(), TimeUnit.SECONDS)
            .build();

    // все вызовы события с авто-вытеснением (event Id + memberId + unigue key) -> long event period in seconds
    private final static Cache<EventIdAndMemberIdAndUniqueIdKey, TimePeriodAndCallQuantity> advancedEventCalls = Caffeine.newBuilder()
            .scheduler(Scheduler.systemScheduler())
            .expireAfter(new Expiry<EventIdAndMemberIdAndUniqueIdKey, TimePeriodAndCallQuantity>() {
                @Override
                public long expireAfterCreate(EventIdAndMemberIdAndUniqueIdKey key, TimePeriodAndCallQuantity value, long currentTime) {
                    return TimeUnit.SECONDS.toNanos(value.getTimePeriod());
                }

                @Override
                public long expireAfterUpdate(EventIdAndMemberIdAndUniqueIdKey key, TimePeriodAndCallQuantity value, long currentTime, long currentDuration) {
                    return currentDuration;
                }

                @Override
                public long expireAfterRead(EventIdAndMemberIdAndUniqueIdKey key, TimePeriodAndCallQuantity value, long currentTime, long currentDuration) {
                    return currentDuration;
                }
            })
            .removalListener((EventIdAndMemberIdAndUniqueIdKey key, TimePeriodAndCallQuantity value, RemovalCause cause)->{
                if(cause==RemovalCause.EXPIRED){
                    advancedEventCallCounters.asMap().compute(key.eventIdAndMemberId(), (k, counter)->{
                        if(counter==null) return null;
                        long newValue = counter.addAndGet(Math.abs(value.getCallQuantity())*-1);
                        return (newValue>0)? counter : null;
                    });
                }
            })
            .build();

    // хранение кулдаунов конкретного события для конкретного участника ((eventId + memberId) -> ttl)
    private final static Cache<EventIdAndMemberIdKey, Integer> eventCoolDownCalls= Caffeine.newBuilder()
            .expireAfter(new Expiry<EventIdAndMemberIdKey,Integer>(){
                @Override
                public long expireAfterCreate(EventIdAndMemberIdKey key, Integer value, long currentTime) {
                    return TimeUnit.SECONDS.toNanos(value);
                }

                @Override
                public long expireAfterUpdate(EventIdAndMemberIdKey key, Integer value, long currentTime, long currentDuration) {
                    return currentDuration;
                }

                @Override
                public long expireAfterRead(EventIdAndMemberIdKey key, Integer value, long currentTime, long currentDuration) {
                    return currentDuration;
                }
            })
            .build();



    @Autowired
    @Lazy
    public void setCommandDispatcher(CommandDispatcher commandDispatcher){
        this.commandDispatcher = commandDispatcher;
    }

    public void executeRequiredChatEvents(VkMessage message){
        long chatId = ChatUtils.extractConversationId(message.getPeerId());
        long fromId = message.getFromId();
        int chatMessageId = message.getConversationMessageId();

        int callerRole = memberService.getMemberRolePriority(chatId, fromId);
        ImmutableMap<ChatEventType, ImmutableSet<EventDto>> eventsCache = eventService.getCachedChatEvents(chatId);
        TimeZoneType chatTimeZone = chatService.getChatTimeZone(chatId);
        LocalTime nowInTheChat = LocalTime.now(chatTimeZone.getZoneOffset());
        Instant nowInstant = Instant.now();

        VkAction action = message.getAction();
        List<VkMessageAttachment> attachments = message.getAttachments();
        String userText = Optional.ofNullable(message.getText()).orElse("").trim();
        List<ForeignMessage> fwMessages = message.getFwdMessages();

        Map<VkMessageAttachmentType, List<VkMessageAttachment>> attachmentMap=Collections.emptyMap();

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
                            .collect(Collectors.groupingBy(VkMessageAttachment::getType));
                }case FWD_MESSAGES -> {
                    if(fwMessages.isEmpty()) continue;
                }
            }
            requiredEvents = eventsCache.get(chatEventType);
            if(requiredEvents!=null){
                for(EventDto currentEvent: requiredEvents){

                    if(!eventService.isEventRoleHighEnough(currentEvent.getRolePriority(), callerRole)){
                        continue;
                    }if(currentEvent.getExceptionalMembers().contains(fromId)){
                        continue;
                    }
                    Integer CDPeriod = currentEvent.getCDPeriodSec();
                    if(CDPeriod!=null&&CDPeriod>0){
                        if(eventCoolDownCalls.getIfPresent(new EventIdAndMemberIdKey(currentEvent.getId(), fromId))!=null){
                            continue;
                        }
                    }
                    if(currentEvent.getStartDayTime()!=null){
                        if(!isNowInDailyRange(currentEvent.getStartDayTime(), currentEvent.getEndDayTime(), nowInTheChat)){
                         continue;
                        }
                    }
                    Integer newMembersPeriod = currentEvent.getNewMembersPeriodSec();
                    if(newMembersPeriod!=null&&newMembersPeriod>0){
                        if(!isNewMember(nowInstant, memberService.getFirstAppearance(chatId,fromId).orElse(nowInstant),newMembersPeriod)){
                            continue;
                        }
                    }
                    boolean isAdvancedEvent = currentEvent.getAEPeriodSec()!=null;
                    switch (currentEvent.getType()){
                        case ANY_MESSAGE -> {
                            if(action==null){
                                executeEvent(chatId, fromId, null, currentEvent,1, chatMessageId);
                            } continue;
                        }case FWD_QUANTITY -> {
                            int fwdQuantity = countForwardedMessages(message.getFwdMessages());
                            if(!isAdvancedEvent&&fwdQuantity<Integer.parseInt(currentEvent.getArgument())) continue;
                            executeEvent(chatId, fromId, null, currentEvent, fwdQuantity, chatMessageId);
                            continue;
                        }
                    }
                    switch (chatEventType){
                        case ACTION -> {
                            handleActionEvent(currentEvent, action, fromId, chatId, chatMessageId);
                        }case TEXT -> {
                            handleTextEvent(currentEvent, userText, attachments.size(), fromId, chatId,  chatMessageId, message.getExpireTTL()!=null, isAdvancedEvent);
                        }case ATTACHMENTS -> {
                            handleAttachmentEvent(currentEvent, attachments, attachmentMap, fromId, chatId, chatMessageId, isAdvancedEvent);
                        }
                    }
                }
            }
            if(chatEventType== ACTION){
                return; // если текущее событие - action, то не может быть text, attachment

              }
        }
    }
    private void handleActionEvent(@NonNull EventDto eventDto,@NonNull VkAction action, long fromId, long chatId, int chatMessageId){
        MyEventType eventType = eventDto.getType();
        if(eventType.getChatEventType()!=ACTION) return;

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
             case WITH_SUBSCRIPTION, WITHOUT_SUBSCRIPTION -> {
                 if(memberId!=null) fromId = memberId;
                 GroupIdAndUserIdKey key = new GroupIdAndUserIdKey(Long.parseLong(eventDto.getArgument()), fromId);
                 boolean isMember = Boolean.TRUE.equals(COMMUNITY_SUBSCRIPTIONS_CACHE.get(key, k ->{
                         try {
                             return vkChatClient.isCommunityMember(k.groupId(), k.userId());
                         } catch (Exception e){
                             log.warn("error execute method isCommunityMember, group: {}; user {}", eventDto.getArgument(), k.userId());
                             return false;
                         }
                     }));
                 if((eventType==WITH_SUBSCRIPTION&&!isMember)||(eventType==WITHOUT_SUBSCRIPTION&&isMember)){
                     return;
                 }
             }
         }
         executeEvent(chatId, fromId, memberId, eventDto, 1, chatMessageId);
     }

     private void handleAttachmentEvent(@NonNull EventDto eventDto, @NonNull List <VkMessageAttachment> allAttachmentsList, @NonNull Map<VkMessageAttachmentType, List<VkMessageAttachment>> allAttachmentsMap, long fromId, long chatId, int chatMessageId, boolean isAdvancedEvent){
        MyEventType eventType = eventDto.getType();
        if(eventType.getChatEventType()!=ATTACHMENTS) return;

        String arg = eventDto.getArgument();
        Integer intArg = arg!=null&&eventType.getArgumentType()==INTEGER?Integer.parseInt(arg):null;

        VkMessageAttachmentType vkTypeToExecute = eventType.getVkAttachmentType().orElse(null);

        List<VkMessageAttachment> currentTypeAttachments;
        if (eventType==ATTACHMENT_QUANTITY){
            currentTypeAttachments = allAttachmentsList;
        }else{
            if(vkTypeToExecute==null){
                log.warn("attachment event {} returned empty Optional<MessageAttachmentType>", eventType);
                return;
            }
            currentTypeAttachments = allAttachmentsMap.get(vkTypeToExecute);
        }

        if(currentTypeAttachments==null||currentTypeAttachments.isEmpty()){
            return;  // в сообщении вообще нет вложений искомого типа
        }

        switch (eventType){
            case LONG_VOICE_MESSAGE, SHORT_VOICE_MESSAGE -> {
                AudioMessage voiceMessage = currentTypeAttachments.get(0).getAudioMessage(); // голосовое всегда одно как вложение
                int duration = voiceMessage.getDuration();
                if(eventType==MyEventType.SHORT_VOICE_MESSAGE) {
                    if(duration>=intArg) return;
                }else{
                    if(duration<=intArg) return;
                }
                executeEvent(chatId, fromId, null, eventDto,1, chatMessageId);
                return;
            }
            case VIDEO_MESSAGE, VK_CLIP-> {
                Video video = currentTypeAttachments.get(0).getVideo();  // видеосообщение / клип всегда одни как вложение
                VideoType videoType = video.getType();
                if(eventType==MyEventType.VIDEO_MESSAGE) {
                    if(videoType!=VideoType.VIDEO_MESSAGE) return;
                }else{
                    if(videoType!=VideoType.SHORT_VIDEO) return;
                }
                executeEvent(chatId, fromId, null, eventDto, 1, chatMessageId);
                return;
            }
            case VIDEO -> {
                if(currentTypeAttachments.get(0).getVideo().getType()!=VideoType.VIDEO) return;
            }
        }
        if(!isAdvancedEvent&&intArg!=null){
            if(currentTypeAttachments.size()<intArg) {
                return;   // вложения искомого типа есть, но их недостаточно
            }
        }
        executeEvent(chatId, fromId, null, eventDto, currentTypeAttachments.size(), chatMessageId);
    }

    private void handleTextEvent(@NonNull EventDto eventDto, @NonNull String userText, int attachmentsSize, long fromId, long chatId, int chatMessageId, boolean isSelfDestructing, boolean isAdvancedEvent){
        MyEventType eventType = eventDto.getType();
        if(eventType.getChatEventType()!=TEXT) return;

        String arg = eventDto.getArgument();
        Integer intArg = arg!=null&&eventType.getArgumentType()==INTEGER?Integer.parseInt(arg):null;

        int callQuantity = 0;

        switch (eventType){
            case WORD_FILTER -> {
                String lowerCaseText = userText.toLowerCase();
                String lowerCaseArg = arg.toLowerCase();
                int index = 0;
                while ((index = lowerCaseText.indexOf(lowerCaseArg, index)) != -1){
                    callQuantity++;
                    if(!isAdvancedEvent) break;
                    index += lowerCaseArg.length();
                }

            }case STRICT_WORD_FILTER -> {
                Pattern p = STRICT_WORD_FILTER_PATTERN_CACHE.get(arg,
                        a-> Pattern.compile("(?<!\\p{L}|\\p{N})" + Pattern.quote(arg) + "(?!\\p{L}|\\p{N})", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS)
                );
                Matcher matcher = p.matcher(userText);
                while(matcher.find()){
                        callQuantity++;
                        if(!isAdvancedEvent) break;
                }

            }case SHORT_MESSAGE ->{
                if(attachmentsSize>0||userText.length()>=intArg) return;
                callQuantity = 1;

            }case MAXIMUM_SYMBOLS -> {
                callQuantity = userText.length();

            }case EMOJI_QUANTITY -> {
                callQuantity = TextUtils.countEmojis(userText);

            }case ROW_QUANTITY -> {
                callQuantity = 1;
                for(int i=0; i<userText.length(); i++){
                    if(userText.charAt(i)=='\n') callQuantity++;
                }
            }
            case ALL_MENTION -> {
                Matcher matcher = PUSH_ALL_PATTERN.matcher(userText);
                while(matcher.find()){
                      callQuantity++;
                      if(!isAdvancedEvent) break;
                }
            }
            case ONLINE_MENTION -> {
                Matcher matcher = PUSH_ONLINE_PATTERN.matcher(userText);
                while(matcher.find()){
                    callQuantity++;
                    if(!isAdvancedEvent) break;
                }
            }
            case ANY_LINK -> {
                Matcher matcher = URL_PATTERN.matcher(userText);
                while(matcher.find()){
                    callQuantity++;
                    if(!isAdvancedEvent) break;
                }
            }
            case ZALGO -> {
                if (!isZalgo(userText)) return;
                callQuantity = 1;
            }
            case CHAT_INVITE_LINK -> {
                Matcher matcher = VK_CHAT_INVITE_LINK_PATTERN.matcher(userText);
                while(matcher.find()){
                    callQuantity++;
                    if(!isAdvancedEvent) break;
                }
            }
            case CAPS -> {
                if (!isMostlyCaps(userText)) return;
                callQuantity=1;
            }
            case REGEX_FILTER -> {
                Pattern p = REGEX_PATTERN_CACHE.get(arg, a ->
                        Pattern.compile(a, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS));
                Matcher matcher = p.matcher(userText);
                while(matcher.find()){
                    callQuantity++;
                    if(!isAdvancedEvent) break;
                }
            }
            case SELF_DESTRUCTING_MESSAGE -> {
                if (!isSelfDestructing) return;
                callQuantity=1;
            }
            case ANY_PUSH_QUANTITY -> {
                Matcher matcher = ANY_PUSH_PATTERN.matcher(userText);
                while(matcher.find()){
                    callQuantity++;
                }
            }
            case SAME_MESSAGES -> {
                EventIdAndMemberIdKey key = new EventIdAndMemberIdKey(eventDto.getId(), fromId);
                MessageCounter value = SAME_MESSAGES_CACHE.asMap().compute(key, (k, existing) -> {
                    if(existing==null){
                        return new MessageCounter(userText, 1);
                    }
                    if(existing.getText().equalsIgnoreCase(userText)){
                        return new MessageCounter(existing.getText(), existing.getCount()+1);
                    }
                    return new MessageCounter(userText, 1);
                });
                callQuantity = value.getCount();
            }
        }
        if(eventType!=SHORT_MESSAGE){
            if(!isAdvancedEvent&&intArg!=null&&callQuantity<intArg) return;
        }
        executeEvent(chatId, fromId, null, eventDto,callQuantity, chatMessageId);
    }

   private void executeEvent(long chatId, long fromId, @Nullable Long memberId, @NonNull EventDto eventDto, int callQuantity, int chatMessageId){
       if(callQuantity<=0) return;
       EventIdAndMemberIdKey eventKey=null;

       if(eventDto.getAEMaxUsage()!=null){
           eventKey = new EventIdAndMemberIdKey(eventDto.getId(),fromId);

           int allEventCalls = advancedEventCallCounters.get(eventKey, k-> new AtomicInteger()).addAndGet(callQuantity);
           advancedEventCalls.put(
                   new EventIdAndMemberIdAndUniqueIdKey(eventKey, chatMessageId),
                   new TimePeriodAndCallQuantity(eventDto.getAEPeriodSec(),callQuantity)
           );
           if(allEventCalls<eventDto.getAEMaxUsage()){

               return;
           }
       }
       Integer cdPeriod = eventDto.getCDPeriodSec();
       if(cdPeriod!=null&&cdPeriod>0){
           if(eventKey==null){
              eventKey = new EventIdAndMemberIdKey(eventDto.getId(), fromId);
           }
           Integer existingValue = eventCoolDownCalls.asMap().putIfAbsent(eventKey, cdPeriod);
           if(existingValue!=null){  // другой поток уже успел завладеть
               return;
           }
       }

       if(eventDto.getFullCommand()!=null){
           String fullCommand = USER_PARAMETER_PATTERN
                   .matcher(eventDto.getFullCommand())
                   .replaceAll(createMemberLink(fromId));

           if(memberId!=null){
               fullCommand = MEMBER_ID_PATTERN
                       .matcher(fullCommand)
                       .replaceAll(createMemberLink(memberId));
           }
           try{
               commandDispatcher.dispatch(
                       commandMapper.toCommandMessageDto(chatId, eventDto.getCreatorId(), fullCommand, true)
               );
           }catch (Exception e){
               log.warn("error while execution event {} in chat {}.", eventDto.getId(), chatId, e);
               try {
                   String message = "Ваше событие с командой «%s» завершилось с ошибкой. Возможно, вам следует его удалить."
                           .formatted(eventDto.getFullCommand());
                   vkChatClient.sendText(message, convertToPeerId(chatId), true);
               } catch (Exception ex) {
                   log.warn("chat {}: Failed to send notification about error while execution event {}", chatId, eventDto.getId(), ex);
               }
           }
       }if(eventDto.isDelete()){
           if(!memberService.isChatAdmin(chatId,fromId)){
               try {
                   vkChatClient.deleteOneMessage(convertToPeerId(chatId), chatMessageId);
               } catch (Exception e) {
                   log.warn("chat {} error: could not delete not-chat-admin message {}",chatId,chatMessageId, e);
               }
           }
       }
   }
    private int countForwardedMessages(List<ForeignMessage> fwMessages){
        if(fwMessages==null||fwMessages.isEmpty()){
            return 0;
        }
        int count = 0;
        for(ForeignMessage msg: fwMessages) {
            count++;
            count+=countForwardedMessages(msg.getFwdMessages());
        }
        return count;
    }

    private boolean isNowInDailyRange(@NonNull LocalTime start, @NonNull LocalTime end,@NonNull LocalTime now){
        if(start.equals(end)){
            return true;
        }
        if(start.isBefore(end)) {// 08:00–23:00
            return !now.isBefore(start)&&now.isBefore(end);
        }else{ // 23:00–08:00
            return !now.isBefore(start)||now.isBefore(end);
        }
    }
    private boolean isNewMember(@NonNull Instant now, @NonNull Instant memberJoinDate, int period){
        return !memberJoinDate.isBefore(now.minusSeconds(period));
    }




}
