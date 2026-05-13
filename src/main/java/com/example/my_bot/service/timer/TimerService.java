package com.example.my_bot.service.timer;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.command.CommandRegistry;
import com.example.my_bot.entity.TimerEntity;
import com.example.my_bot.enumeration.timer.TimerType;
import com.example.my_bot.exception.command.CommandAccessDeniedException;
import com.example.my_bot.exception.command.CommandArgumentTooLongException;
import com.example.my_bot.exception.command.UserCommandNotFoundException;
import com.example.my_bot.exception.timer.*;
import com.example.my_bot.repository.TimerRepository;
import com.example.my_bot.resolver.UserInputResolver;
import com.example.my_bot.service.CommandAccessService;
import com.example.my_bot.service.MemberService;
import com.example.my_bot.service.chat.ChatService;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.example.my_bot.enumeration.DefaultRole.isDefaultRole;
import static com.example.my_bot.enumeration.timer.TimerType.*;
import static com.example.my_bot.utils.TextUtils.cutDefaultPrefix;
import static com.example.my_bot.utils.TimeUtils.formatDurationFromSeconds;
import static java.lang.String.format;

@Slf4j
@Service
@RequiredArgsConstructor
public class TimerService {
    private final TimerRepository timerRepository;
    private final CommandAccessService commandAccessService;
    private final MemberService memberService;
    private final TimerExecutionService timerExecutionService;
    private final ChatService chatService;
    private CommandRegistry commandRegistry;

    private static final long MIN_INTERVAL_BETWEEN_EXECUTING = 5*60;
    private static final long MAX_INTERVAL_BETWEEN_EXECUTING = 2_592_000;
    private static final String FORMATTED_MIN_INTERVAL = formatDurationFromSeconds(MIN_INTERVAL_BETWEEN_EXECUTING, true);
    private static final String FORMATTED_MAX_INTERVAL = formatDurationFromSeconds(MAX_INTERVAL_BETWEEN_EXECUTING, true);
    private static final Instant MAX_DATE_FOR_ONCE_TIMER =  Instant.parse("2046-12-31T23:59:59Z");
    private static final int MAX_TIMERS = 30;
    private static final int SYSTEM_MAX_EXECUTION_LIMIT = 100;
    private static final int TIMER_COMMAND_ARGUMENT_MAX_LENGTH = 100;


    @Autowired
    @Lazy
    public void setCommandRegistry(CommandRegistry commandRegistry) {
        this.commandRegistry = commandRegistry;
    }

    public int getMaxTimers() {
        return MAX_TIMERS;
    }


    @Transactional
    public TimerEntity createOnceTimer(long chatId, @NonNull LocalDateTime executionDate, @NonNull String fullCommand, long fromId){

        ZoneOffset chatTimeZoneOffset = chatService.getChatTimeZone(chatId).getZoneOffset();
        Instant executionInstantDate = executionDate.atZone(chatTimeZoneOffset).toInstant();
        checkNextExecutionDateCondition(executionInstantDate);
        fullCommand = cutDefaultPrefix(fullCommand);
        checkTimersLimitAndCommandConditions(chatId, fromId, fullCommand);
        TimerEntity savedTimer = timerRepository.save(
                new TimerEntity(chatId, fromId, ONCE, fullCommand.trim(), executionInstantDate)
        );
        timerExecutionService.putTimerToSchedulerIfExecutionIsNear(savedTimer);
        return savedTimer;

    }
    @Transactional
    public TimerEntity createEachTimer(long chatId, long intervalInSeconds, @NonNull String fullCommand, long fromId){

        Instant now = Instant.now();

        if(intervalInSeconds<MIN_INTERVAL_BETWEEN_EXECUTING){
            throw new TimerIntervalOutOfBoundsException("Нельзя создать таймер, который выполняется чаще чем раз в %s"
                    .formatted(FORMATTED_MIN_INTERVAL));
        }if(intervalInSeconds>MAX_INTERVAL_BETWEEN_EXECUTING){
            throw new TimerIntervalOutOfBoundsException("Максимальный интервал для данного типа таймера — %s"
                    .formatted(FORMATTED_MAX_INTERVAL));
        }
        fullCommand = cutDefaultPrefix(fullCommand);
        checkTimersLimitAndCommandConditions(chatId, fromId, fullCommand);

        Instant nextExecution = now.plusSeconds(intervalInSeconds);

        TimerEntity savedTimer = timerRepository.save(
                new TimerEntity(chatId, fromId, TimerType.EACH, fullCommand.trim(), intervalInSeconds, nextExecution)
        );
        timerExecutionService.putTimerToSchedulerIfExecutionIsNear(savedTimer);
        return savedTimer;

    }
    @Transactional
    public TimerEntity createDailyTimer(long chatId, @NonNull LocalTime dailyTime, @NonNull String fullCommand, long fromId){

        fullCommand = cutDefaultPrefix(fullCommand);
        checkTimersLimitAndCommandConditions(chatId, fromId, fullCommand);

        ZoneOffset chatTimeZoneOffset = chatService.getChatTimeZone(chatId).getZoneOffset();
        ZonedDateTime now = ZonedDateTime.now(chatTimeZoneOffset);   // время чата сейчас
        ZonedDateTime next = now.with(dailyTime); //  текущая дата, но с нужным временем
        if (next.isBefore(now)) {
            next = next.plusDays(1); // если время уже прошло сегодня, то беру завтра
        }
        Instant nextExecution = next.toInstant();
        TimerEntity savedTimer = timerRepository.save(
                new TimerEntity(chatId, fromId, TimerType.DAILY, fullCommand.trim(), nextExecution)
        );
        timerExecutionService.putTimerToSchedulerIfExecutionIsNear(savedTimer);
        return savedTimer;
    }

    @Transactional
    public void deleteTimerById(long timerId){
        timerExecutionService.cancelTaskAndRemoveFromCache(timerId);
        timerRepository.deleteById(timerId);
    }

   @Transactional
    public Instant incrementNextExecutionAndExecutionCounter(long timerId){
        TimerEntity timer = timerRepository.findById(timerId)
                .orElseThrow(()->new TimerNotFoundException(timerId));

        if(timer.getType()==ONCE){
            throw new IllegalTimerTypeException(ONCE);
        }
        int nextExecutionCounter = timer.getExecutionCounter()+1;
        Optional<Integer> customLimit = timer.getOptionalCustomExecutionLimit();
        if(nextExecutionCounter>=SYSTEM_MAX_EXECUTION_LIMIT||(customLimit.isPresent()&&nextExecutionCounter>=customLimit.get())){
            throw new TimerHasReachedExecutionLimitException();
        }
       Instant newNextExecution;
       Instant now = Instant.now();

       if(timer.getType()==EACH){
           newNextExecution = now.plusSeconds(timer.getIntervalSeconds());  //всегда приплюсовываю к текущему моменту
       }else {
           newNextExecution = timer.getNextExecution();
           do{
             newNextExecution = newNextExecution.plus(1, ChronoUnit.DAYS);
           }while (newNextExecution.isBefore(now));  // догоняю ежедневный таймер, если он отстал
       }
        timer.setNextExecution(newNextExecution);
        timer.setExecutionCounter(timer.getExecutionCounter()+1);
        return newNextExecution;
    }

    @Transactional
    public void setCustomExecutionLimit(long timerId, int newLimit){
        TimerEntity timer = timerRepository.findById(timerId)
                .orElseThrow(()->new TimerNotFoundException(timerId));

        if(timer.getType()==ONCE){
            throw new IllegalTimerTypeException(ONCE);
        }
        Optional<Integer> currentCustomLimit = timer.getOptionalCustomExecutionLimit();
        if((currentCustomLimit.isPresent()&&currentCustomLimit.get()==newLimit)){
            throw new TimerAlreadyHasThatExecutionLimitException();
        }if(newLimit>SYSTEM_MAX_EXECUTION_LIMIT){
            throw new IncorrectTimerExecutionLimitException(
                    "Максимальный лимит срабатывания для таймеров — %d.".formatted(SYSTEM_MAX_EXECUTION_LIMIT)
            );
        }if(newLimit<=timer.getExecutionCounter()){
            throw new IncorrectTimerExecutionLimitException(
                    "Данный таймер уже успел выполниться %d раз, поэтому выберите более высокое число в качестве лимита срабатывания."
                            .formatted(timer.getExecutionCounter())
            );
        }timer.setCustomExecutionLimit(newLimit);
    }

    @Transactional
    public void changeNextExecutionForEachTimer(long timerId, @NonNull LocalDateTime newNextExecution){

        TimerEntity timer = timerRepository.findById(timerId)
                .orElseThrow(()->new TimerNotFoundException(timerId));
        if(!timer.getType().equals(EACH)){
            throw new IllegalTimerTypeException(timer.getType());
        }
        ZoneOffset chatTimeZoneOffset = chatService.getChatTimeZone(timer.getChatId()).getZoneOffset();
        Instant newNextExecutionInstant = newNextExecution.atZone(chatTimeZoneOffset).toInstant();
        if(newNextExecutionInstant.compareTo(timer.getNextExecution())==0){
            throw new TimerAlreadyHasThatNextExecutionException();
        }
        checkNextExecutionDateCondition(newNextExecutionInstant);
        timer.setNextExecution(newNextExecutionInstant);
        timerExecutionService.cancelTaskAndRemoveFromCache(timerId);
        timerExecutionService.putTimerToSchedulerIfExecutionIsNear(timer);
    }

    public List<TimerEntity> getChatTimersSortedByIdAsc(long chatId){
        return timerRepository.findByChatIdOrderByIdAsc(chatId);
    }
    public List<TimerEntity> getAllTimersWithNextExecutionLessThan(@NonNull Instant requiredDateTime, @Nullable Set<Long> excludedTimerIds){

        if(excludedTimerIds==null||excludedTimerIds.isEmpty()){
            return timerRepository.findAllTimersWithNextExecutionLessThan(requiredDateTime);
        } return timerRepository.findAllTimersWithNextExecutionLessThan(requiredDateTime, excludedTimerIds);

    }

    private void checkTimersLimitAndCommandConditions(long chatId, long fromId, @NonNull String fullCommand){
        if(timerRepository.countByChatId(chatId)>=MAX_TIMERS){
            throw new TooManyTimersException();
        }
        String userCommand = UserInputResolver.splitFullCommand(fullCommand)[0];
        if(fullCommand.length()-userCommand.length()>TIMER_COMMAND_ARGUMENT_MAX_LENGTH){
            throw new CommandArgumentTooLongException(TIMER_COMMAND_ARGUMENT_MAX_LENGTH);
        }
        Command annotation = commandRegistry.getCommandAnnotation(userCommand).orElseThrow(()->
                new UserCommandNotFoundException(userCommand));
        if(!annotation.eventable()){
            throw new CannotUseThisCommandForTimerException(annotation.mainCommandName());
        }
        int userRolePriority = memberService.getMemberRolePriority(chatId, fromId);
        boolean accessToCmd = commandAccessService.checkCommandAuthorization(chatId, userCommand, userRolePriority, fromId);
        if(!accessToCmd){
            throw new CommandAccessDeniedException(fromId,userCommand);
        }
    }
    private void checkNextExecutionDateCondition(@NonNull Instant nextExecution){
        if(nextExecution.minusSeconds(MIN_INTERVAL_BETWEEN_EXECUTING).isBefore(Instant.now())){
            throw new TimerDateOutOfBoundsException("Создать можно только таймер с датой, которая будет минимум через %s от текущей даты."
                    .formatted(FORMATTED_MIN_INTERVAL));
        }if(nextExecution.isAfter(MAX_DATE_FOR_ONCE_TIMER)){
            throw new TimerDateOutOfBoundsException("Вы ввели дату, которая находится слишком далеко в будущем. Выберите более раннюю дату.");
        }

    }

}
