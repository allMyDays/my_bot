package com.example.my_bot.service.timer;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.command.CommandRegistry;
import com.example.my_bot.config.CaffeineCacheManager;
import com.example.my_bot.entity.TimerEntity;
import com.example.my_bot.enumeration.timer.TimerType;
import com.example.my_bot.exception.command.CommandAccessDeniedException;
import com.example.my_bot.exception.command.UserCommandNotFoundException;
import com.example.my_bot.exception.timer.*;
import com.example.my_bot.repository.TimerRepository;
import com.example.my_bot.service.CommandAccessService;
import com.example.my_bot.service.MemberService;
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
import java.util.Set;
import java.util.concurrent.ScheduledFuture;

import static com.example.my_bot.enumeration.DefaultRole.isDefaultRole;
import static com.example.my_bot.enumeration.timer.TimerType.*;
import static com.example.my_bot.utils.TimeUtils.formatDurationFromSeconds;
import static java.lang.String.format;
import static java.time.temporal.ChronoUnit.DAYS;

@Slf4j
@Service
@RequiredArgsConstructor
public class TimerService {
    private final TimerRepository timerRepository;
    private final CommandAccessService commandAccessService;
    private final MemberService memberService;
    private final TimerExecutionService timerExecutionService;
    private CommandRegistry commandRegistry;

    private static final long MIN_INTERVAL_BETWEEN_EXECUTING = 5*60;
    private static final long MAX_INTERVAL_BETWEEN_EXECUTING = 2_592_000;
    private static final String FORMATTED_MIN_INTERVAL = formatDurationFromSeconds(MIN_INTERVAL_BETWEEN_EXECUTING, true);
    private static final String FORMATTED_MAX_INTERVAL = formatDurationFromSeconds(MAX_INTERVAL_BETWEEN_EXECUTING, true);
    private static final Instant MAX_DATE_FOR_ONCE_TIMER =  Instant.parse("2036-12-31T23:59:59Z");
    private static final int MAX_TIMERS = 30;


    @Autowired
    @Lazy
    public void setCommandRegistry(CommandRegistry commandRegistry) {
        this.commandRegistry = commandRegistry;
    }

    public int getMaxTimers() {
        return MAX_TIMERS;
    }


    @Transactional
    public TimerEntity createOnceTimer(long chatId, @NonNull LocalDateTime executionDate, @NonNull String fullCommandWithArgs, long fromId){

        Instant executionInstantDate = executionDate.atZone(ZoneId.of("Europe/Moscow")).toInstant();
        checkNextExecutionDateCondition(executionInstantDate);
        checkTimersLimitAndCommandUsageAbility(chatId, fromId, fullCommandWithArgs);
        TimerEntity savedTimer = timerRepository.save(
                new TimerEntity(chatId, fromId, ONCE, fullCommandWithArgs.trim(), executionInstantDate)
        );
        timerExecutionService.putTimerToSchedulerIfExecutionIsNear(savedTimer);
        return savedTimer;

    }
    @Transactional
    public TimerEntity createEachTimer(long chatId, long intervalInSeconds, @NonNull String fullCommandWithArgs, long fromId){

        Instant now = Instant.now();

        if(intervalInSeconds<MIN_INTERVAL_BETWEEN_EXECUTING){
            throw new TimerIntervalOutOfBoundsException("Нельзя создать таймер, который выполняется чаще чем раз в %s"
                    .formatted(FORMATTED_MIN_INTERVAL));
        }if(intervalInSeconds>MAX_INTERVAL_BETWEEN_EXECUTING){
            throw new TimerIntervalOutOfBoundsException("Максимальный интервал для данного типа таймера — %s"
                    .formatted(FORMATTED_MAX_INTERVAL));
        }
        checkTimersLimitAndCommandUsageAbility(chatId, fromId, fullCommandWithArgs);

        Instant nextExecution = now.plusSeconds(intervalInSeconds);

        TimerEntity savedTimer = timerRepository.save(
                new TimerEntity(chatId, fromId, TimerType.EACH, fullCommandWithArgs.trim(), intervalInSeconds, nextExecution)
        );
        timerExecutionService.putTimerToSchedulerIfExecutionIsNear(savedTimer);
        return savedTimer;

    }
    @Transactional
    public TimerEntity createDailyTimer(long chatId, @NonNull LocalTime dailyTime, @NonNull String fullCommandWithArgs, long fromId){
        checkTimersLimitAndCommandUsageAbility(chatId, fromId, fullCommandWithArgs);

        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Europe/Moscow"));   // время чата сейчас
        ZonedDateTime next = now.with(dailyTime); //  текущая дата, но с нужным временем
        if (next.isBefore(now)) {
            next = next.plusDays(1); // если время уже прошло сегодня, то беру завтра
        }
        Instant nextExecution = next.toInstant();
        TimerEntity savedTimer = timerRepository.save(
                new TimerEntity(chatId, fromId, TimerType.DAILY, fullCommandWithArgs.trim(), nextExecution)
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
    public Instant incrementNextExecutionForPeriodicTimer(long timerId){
        TimerEntity timer = timerRepository.findById(timerId)
                .orElseThrow(()->new TimerNotFoundException(timerId));

        if(timer.getType()==ONCE){
            throw new IllegalTimerTypeException(ONCE);
        }

        ZoneId zone = ZoneId.of("Europe/Moscow");

       Instant newNextExecution;

       if(timer.getType()==EACH){
           newNextExecution = Instant.now().plusSeconds(timer.getIntervalSeconds());  //всегда приплюсовываю к текущему моменту
       }else {
           ZonedDateTime next = timer.getNextExecution().atZone(zone).plusDays(1);
           ZonedDateTime now = ZonedDateTime.now(zone);
           if (next.isBefore(now)) {   // таймер отстал, догоняю по времени
               next = next.plusDays(
                       ChronoUnit.DAYS.between(next.toLocalDate(), now.toLocalDate()) + 1
               );
           }newNextExecution = next.toInstant();
       }
        timer.setNextExecution(newNextExecution);
        return newNextExecution;
    }

    public List<TimerEntity> getAllChatTimersSortedByIdAsc(long chatId){
        return timerRepository.findByChatIdOrderByIdAsc(chatId);
    }
    public List<TimerEntity> getAllTimersWithNextExecutionLessThan(@NonNull Instant requiredDateTime, @Nullable Set<Long> excludedTimerIds){

        if(excludedTimerIds==null||excludedTimerIds.isEmpty()){
            return timerRepository.findAllTimersWithNextExecutionLessThan(requiredDateTime);
        } return timerRepository.findAllTimersWithNextExecutionLessThan(requiredDateTime, excludedTimerIds);

    }

    private void checkTimersLimitAndCommandUsageAbility(long chatId, long fromId, @NonNull String fullCommandWithArgs){
        if(timerRepository.countByChatId(chatId)>=MAX_TIMERS){
            throw new TooManyTimersException();
        }String userCommand = fullCommandWithArgs.trim().split("\\s+")[0];
        Command annotation = commandRegistry.getCommandAnnotation(userCommand).orElseThrow(()->
                new UserCommandNotFoundException(userCommand));
        if(!annotation.eventable()){
            throw new CannotUseThisCommandForTimerException(annotation.mainCommandName());
        }
        int userRolePriority = memberService.getCachedMemberRolePriority(chatId, fromId);
        boolean executable = commandAccessService.checkCommandAuthorization(chatId, userCommand, userRolePriority, fromId);
        if(!executable){
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
