package com.example.my_bot.service.event;
import static com.example.my_bot.enumeration.event.EventArgumentType.*;
import static com.example.my_bot.enumeration.event.MyEventType.WITHOUT_SUBSCRIPTION;
import static com.example.my_bot.enumeration.event.MyEventType.WITH_SUBSCRIPTION;
import static com.example.my_bot.utils.TimeUtils.formatDurationFromSeconds;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.command.CommandRegistry;
import com.example.my_bot.config.AdvancedEventConfig;
import com.example.my_bot.config.CaffeineCacheManager;
import com.example.my_bot.dto.RoleDto;
import com.example.my_bot.dto.event.EventDto;
import com.example.my_bot.dto.member.MemberDto;
import com.example.my_bot.entity.EventEntity;
import com.example.my_bot.enumeration.event.ChatEventType;
import com.example.my_bot.enumeration.event.EventArgumentType;
import com.example.my_bot.enumeration.event.MyEventType;
import com.example.my_bot.exception.command.CannotApplyThisCommandToYourselfException;
import com.example.my_bot.exception.command.CommandAccessDeniedException;
import com.example.my_bot.exception.command.UserCommandNotFoundException;
import com.example.my_bot.exception.event.*;
import com.example.my_bot.exception.member.UserNeverBeenInChatException;
import com.example.my_bot.exception.role.RoleNotFoundException;
import com.example.my_bot.mapper.EventMapper;
import com.example.my_bot.repository.EventRepository;
import com.example.my_bot.resolver.UserInputResolver;
import com.example.my_bot.service.CommandAccessService;
import com.example.my_bot.service.MemberService;
import com.example.my_bot.service.RoleService;
import com.example.my_bot.utils.ChatUtils;
import com.example.my_bot.utils.TextUtils;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import jakarta.annotation.Nullable;
import jakarta.transaction.Transactional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;


@Slf4j
@Service
@RequiredArgsConstructor
public class EventService {
    private final EventRepository eventRepository;
    private final CommandAccessService commandAccessService;
    private final MemberService memberService;
    private final RoleService roleService;
    private CommandRegistry commandRegistry;
    private final CaffeineCacheManager cacheManager;
    private final EventMapper eventMapper;
    private final UserInputResolver userInputResolver;

    private static final int MAX_EVENTS = 100;
    private static final int MAX_SUBSCRIPTION_EVENTS = 10;
    private static final int ADVANCED_EVENT_MAX_PERIOD_IN_SECONDS = 86_400;
    private static final int ADVANCED_EVENT_MIN_PERIOD_IN_SECONDS = 10;
    private static final int ADVANCED_EVENT_MIN_USAGE = 2;
    private static final int DAILY_EVENT_MIN_DIFFERENCE_IN_SECONDS=30*60;
    private static final int COOLDOWN_MAX_PERIOD_IN_SECONDS = 3_600;
    private final static int EXCEPTIONAL_MEMBERS_MAX_LIMIT = 20;

    @Autowired
    @Lazy
    public void setCommandRegistry(CommandRegistry commandRegistry) {
        this.commandRegistry = commandRegistry;
    }

    public int getMaxEvents(){
        return MAX_EVENTS;
    }

    public static int getMaxPeriodForAdvancedEvents(){
        return ADVANCED_EVENT_MAX_PERIOD_IN_SECONDS;
    }


    @Transactional
    public EventEntity createNewEvent(long chatId,
                               @NonNull MyEventType eventType,
                               int rolePriority,
                               @Nullable String userArgument,
                               @NonNull String fullCommand,
                               long fromId){

        userArgument = validateEventArgument(eventType,userArgument);

        if (countChatEvents(chatId)>=getMaxEvents()){
            throw new TooManyEventsException();
        }
        if(eventType==WITH_SUBSCRIPTION||eventType==WITHOUT_SUBSCRIPTION){
            if(getChatEventsWithRequiredTypes(chatId, Set.of(WITH_SUBSCRIPTION, WITHOUT_SUBSCRIPTION)).size()>=MAX_SUBSCRIPTION_EVENTS){
                throw new TooManyEventsException("Превышен лимит на создание событий, связанных с подпиской на сообщества. ");
            }
        }
        if(!roleService.roleExistsByPriority(chatId, rolePriority)){
            throw new RoleNotFoundException();
        }
        int callerRole = memberService.getMemberRolePriority(chatId, fromId);
        roleService.checkRoleInteractionAbility(rolePriority, callerRole);

        fullCommand = TextUtils.cutDefaultPrefix(fullCommand);
        String userCommand = UserInputResolver.splitFullCommand(fullCommand)[0];
        Command annotation = commandRegistry.getCommandAnnotation(userCommand).orElseThrow(()->
                new UserCommandNotFoundException(userCommand));
        if(!annotation.eventable()){
            throw new CannotUseThisCommandForEventException(annotation.mainCommandName());
        }boolean executable = commandAccessService.checkCommandAuthorization(chatId, userCommand, callerRole, fromId);
        if(!executable){
            throw new CommandAccessDeniedException(fromId,userCommand);
        }
        EventEntity savedEvent = eventRepository.save(
                new EventEntity(chatId, eventType, rolePriority, userArgument, fromId, fullCommand)
        );
        invalidateEventsCache(chatId);
        return savedEvent;
    }

    @Transactional
    public EventEntity createNewEvent(long chatId,
                                      @NonNull MyEventType eventType,
                                      @NonNull String roleName,
                                      @Nullable String userArgument,
                                      @NonNull String fullCommand,
                                      long fromId){
        RoleDto foundRole = roleService.getRoleByNameIgnoreCase(chatId, roleName)
                .orElseThrow(RoleNotFoundException::new);

        return createNewEvent(chatId, eventType, foundRole.getRolePriority(), userArgument, fullCommand, fromId);
    }

    public List<EventDto> getEventsSortedByIdInIncreasingOrder(long chatId){
        ImmutableCollection<ImmutableSet<EventDto>> collection = getCachedChatEvents(chatId).values();
        return collection.stream()
                .flatMap(Set::stream)
                .sorted(Comparator.comparing(EventDto::getId))
                .toList();
    }

    @Transactional
    public EventDto setAETimePeriodAndMaxUsage(long eventId, long periodInSeconds, int maxUsage, long fromId){

        EventEntity event = eventRepository.findById(eventId)
                .orElseThrow(()->new EventNotFoundException(eventId));

        if(event.getAEMaxUsage()!=null){
            throw new CurrentEventAlreadyAdvancedException();
        }
        roleService.checkRoleInteractionAbility(event.getRolePriority(), memberService.getMemberRolePriority(event.getChatId(), fromId));

        AdvancedEventConfig advancedConfig = event.getType().getAdvancedEventConfig();
        if(!advancedConfig.isCanBeAdvancedEvent()){
            throw new CurrentEventCannotBeAdvancedException();
        }
        if(periodInSeconds<ADVANCED_EVENT_MIN_PERIOD_IN_SECONDS||periodInSeconds>ADVANCED_EVENT_MAX_PERIOD_IN_SECONDS){
            throw new IncorrectEventArgumentException("Временной период для расширенного события обязан быть от %s до %s"
                    .formatted(formatDurationFromSeconds(ADVANCED_EVENT_MIN_PERIOD_IN_SECONDS,true),formatDurationFromSeconds(ADVANCED_EVENT_MAX_PERIOD_IN_SECONDS, true)));
        }
        int eventMaxUsage = advancedConfig.getMaxUsage();
        if(maxUsage<ADVANCED_EVENT_MIN_USAGE||maxUsage>eventMaxUsage){
            throw new IncorrectEventArgumentException("Для данного типа события, лимит действия должен быть от %d до %d."
                    .formatted(ADVANCED_EVENT_MIN_USAGE,eventMaxUsage));
        }
        event.setAEMaxUsage(maxUsage);
        event.setAEPeriodInSeconds((int)periodInSeconds);
        if(!advancedConfig.isEventArgumentRequired()){
            event.setArgument(null);
        }
        invalidateEventsCache(event.getChatId());

        return eventMapper.toEventDto(event);

    }

    @Transactional
    public EventDto setDailyWorkTime(long eventId, @NonNull LocalTime start, @NonNull LocalTime end, long fromId){

        EventEntity event = eventRepository.findById(eventId)
                .orElseThrow(()->new EventNotFoundException(eventId));
        roleService.checkRoleInteractionAbility(event.getRolePriority(), memberService.getMemberRolePriority(event.getChatId(), fromId));

        long diff = Math.abs(Duration.between(start, end).toSeconds());
        if(diff<DAILY_EVENT_MIN_DIFFERENCE_IN_SECONDS){
            throw new IncorrectEventArgumentException("Событие обязано работать (либо не работать) минимум %s в сутки."
                    .formatted(formatDurationFromSeconds(DAILY_EVENT_MIN_DIFFERENCE_IN_SECONDS,true)));
        }
        event.setStartDayTime(start);
        event.setEndDayTime(end);
        invalidateEventsCache(event.getChatId());

        return eventMapper.toEventDto(event);
    }

    @Transactional
    public EventDto setCDTimePeriod(long eventId, long periodInSeconds, long fromId){

        EventEntity event = eventRepository.findById(eventId)
                .orElseThrow(()->new EventNotFoundException(eventId));

        if(event.getCDPeriodInSeconds()!=null){
            throw new CurrentEventAlreadyHasCooldownException();
        }
        roleService.checkRoleInteractionAbility(event.getRolePriority(), memberService.getMemberRolePriority(event.getChatId(), fromId));

        if(periodInSeconds<0) periodInSeconds=0;
        if(periodInSeconds>COOLDOWN_MAX_PERIOD_IN_SECONDS){
            throw new IncorrectEventArgumentException("Максимальный период для кулдауна — %s"
                    .formatted(formatDurationFromSeconds(COOLDOWN_MAX_PERIOD_IN_SECONDS,true)));
        }
        event.setCDPeriodInSeconds((int)periodInSeconds);
        invalidateEventsCache(event.getChatId());
        return eventMapper.toEventDto(event);
    }

    @Transactional
    public EventDto addMemberToExceptional(long eventId, long memberToAdd, long fromId){

        EventEntity event = eventRepository.findById(eventId)
                .orElseThrow(()->new EventNotFoundException(eventId));

        Set<Long> exceptionalSet = event.getExceptionalMembers();
        if(exceptionalSet.size()>=EXCEPTIONAL_MEMBERS_MAX_LIMIT){
            throw new TooManyExceptionalMembersException();
        }
        roleService.checkRoleInteractionAbility(event.getRolePriority(), memberService.getMemberRolePriority(event.getChatId(), fromId));

        if(exceptionalSet.contains(memberToAdd)){
            throw new IncorrectEventArgumentException("Данный участник уже находится в исключении у этого события.");
        }
        if(memberToAdd==fromId){
            throw new CannotApplyThisCommandToYourselfException();
        }
        Optional<MemberDto> memberInfo = memberService.getCachedMemberInfo(event.getChatId(),memberToAdd);
        if(memberInfo.isEmpty()){
            throw new UserNeverBeenInChatException(memberToAdd);
        }
        if(!isEventRoleHighEnough(event.getRolePriority(), memberInfo.get().getRolePriority())){
            throw new EventCannotReactToThisMemberException("\uD83E\uDD14Данное событие и так не реагирует на этого участника, нет смысла добавлять его в исключения.");

        }
        memberService.checkMemberInteractionAbility(event.getChatId(), fromId, memberToAdd);
        exceptionalSet.add(memberToAdd);
        invalidateEventsCache(event.getChatId());
        return eventMapper.toEventDto(event);
    }

    @Transactional
    public EventDto removeMemberFromExceptional(long eventId, long memberToAdd, long fromId){

        EventEntity event = eventRepository.findById(eventId)
                .orElseThrow(()->new EventNotFoundException(eventId));

        roleService.checkRoleInteractionAbility(event.getRolePriority(), memberService.getMemberRolePriority(event.getChatId(), fromId));

        if(!event.getExceptionalMembers().contains(memberToAdd)){
            throw new IncorrectEventArgumentException("Данный участник не находится в исключении у этого события.");
        }
        if(memberToAdd==fromId){
            throw new CannotApplyThisCommandToYourselfException();
        }
        memberService.checkMemberInteractionAbility(event.getChatId(), fromId, memberToAdd);
        event.getExceptionalMembers().remove(memberToAdd);
        invalidateEventsCache(event.getChatId());
        return eventMapper.toEventDto(event);
    }


    public int countChatEvents(long chatId){
       return Math.toIntExact(getCachedChatEvents(chatId).values().stream().mapToLong(Set::size).sum());

    }


    private String validateEventArgument(@NonNull MyEventType eventType, @Nullable String userArgument){
        EventArgumentType eventArgType = eventType.getArgumentType();
        if(eventArgType== NONE){
            if(userArgument!=null){
                throw new EventTypeNotRequiresArgumentException(eventType);
            }
        }else{
            if(userArgument==null){
                throw new EventArgumentAbsentsException(eventType);
            }
            userArgument = userArgument.trim();
            int argMax = eventType.getArgMax();
            int argMin = eventType.getArgMin();

            if(eventArgType == INTEGER){
                switch (eventType){
                    case WITH_SUBSCRIPTION, WITHOUT_SUBSCRIPTION -> {
                        Long groupId = userInputResolver.getMemberIdByStringInput(userArgument).orElse(null);
                        if(groupId==null||!ChatUtils.isGroupId(groupId)){
                            throw new IncorrectEventArgumentException("Для данного типа события, аргумент обязан быть ссылкой или упоминанием сообщества.");
                        }
                        return groupId.toString();
                    }
                }
                int intArg;
                if(!TextUtils.isValidInteger(userArgument)||((intArg=Integer.parseInt(userArgument))<argMin||intArg>argMax)){
                    throw new IncorrectEventArgumentException("Для данного типа события, аргумент должен быть валидным числом от %d до %d."
                            .formatted(argMin, argMax));
                }
            }else if(eventArgType == STRING){
                if(userArgument.length()<argMin||userArgument.length()>argMax){
                    throw new IncorrectEventArgumentException("Аргумент должен иметь длину символов от %d до %d для данного типа события."
                            .formatted(argMin, argMax));
                }
                switch (eventType){
                    case REGEX_FILTER -> {
                        if(!isValidUserRegex(userArgument)){
                            throw new IncorrectEventArgumentException("Вы ввели некорректное регулярное выражение.");
                        }
                    }
                }
            }

        } return userArgument;

    }

    @Transactional
    public void deleteEventById(long eventId, long fromId){
        EventEntity event = eventRepository.findById(eventId)
                .orElseThrow(()->new EventNotFoundException(eventId));

        int callerRole = memberService.getMemberRolePriority(event.getChatId(), fromId);
        roleService.checkRoleInteractionAbility(event.getRolePriority(), callerRole);
        eventRepository.deleteById(event.getId());
        invalidateEventsCache(event.getChatId());
    }

    public ImmutableMap<ChatEventType, ImmutableSet<EventDto>> getCachedChatEvents(long chatId){
        return cacheManager.getEventsCache().get(chatId, k->{
            List<EventEntity> entities = eventRepository.findByChatId(chatId);
            Map<ChatEventType, ImmutableSet.Builder<EventDto>> builders = new HashMap<>(); // временная карта

            for (EventEntity entity : entities) {
                    ImmutableSet.Builder<EventDto> builder = builders.computeIfAbsent(entity.getType().getChatEventType(), as -> ImmutableSet.builder());
                    builder.add(eventMapper.toEventDto(entity));
            }
            // итоговая карта
            ImmutableMap.Builder<ChatEventType, ImmutableSet<EventDto>> result = new ImmutableMap.Builder<>();
            builders.forEach((key, value) -> result.put(key, value.build()));
            return result.build();
        });

    }
    public Set<EventDto> getChatEventsWithRequiredTypes(long chatId, @NonNull Set<MyEventType> typeSet){

        HashSet<EventDto> eventsToReturn = new HashSet<>();
        if(typeSet.isEmpty()) return eventsToReturn;

        ImmutableMap<ChatEventType, ImmutableSet<EventDto>> eventMap = getCachedChatEvents(chatId);

        eventMap.values().forEach(set->set.forEach(event->{
                    if(typeSet.contains(event.getType())){
                        eventsToReturn.add(event);
                    }
                }
        ));
        return eventsToReturn;
    }

   private void invalidateEventsCache(long chatId){
       cacheManager.getEventsCache().invalidate(chatId);
   }

   private boolean isValidUserRegex(@NonNull String pattern){

        pattern = pattern.trim();
        if (pattern.isEmpty()) return false;

        try {
            Pattern.compile(pattern);
        } catch (PatternSyntaxException e) {
            return false;
        }
        if (pattern.contains(")+") || pattern.contains("++")) return false;
        return true;
    }

    public boolean isEventRoleHighEnough(int eventRole, int memberRole){
        return memberRole<=eventRole;

    }








}
