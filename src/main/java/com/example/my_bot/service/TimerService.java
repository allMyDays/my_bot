package com.example.my_bot.service;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.command.CommandRegistry;
import com.example.my_bot.entity.TimerEntity;
import com.example.my_bot.enumeration.timer.TimerType;
import com.example.my_bot.exception.command.CommandAccessDeniedException;
import com.example.my_bot.exception.command.UserCommandNotFoundException;
import com.example.my_bot.exception.timer.CannotUseThisCommandForTimerException;
import com.example.my_bot.exception.timer.TimerDateOutOfBoundsException;
import com.example.my_bot.exception.timer.TimerIntervalOutOfBoundsException;
import com.example.my_bot.exception.timer.TooManyTimersException;
import com.example.my_bot.repository.TimerRepository;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Annotation;
import java.time.*;
import java.util.List;

import static com.example.my_bot.enumeration.DefaultRole.isDefaultRole;
import static com.example.my_bot.utils.TimeUtils.formatDurationFromSeconds;
import static java.lang.String.format;

@Slf4j
@Service
public class TimerService {
    private final TimerRepository timerRepository;
    private CommandRegistry commandRegistry;
    private final CommandAccessService commandAccessService;
    private final MemberService memberService;

    private final long MIN_INTERVAL_BETWEEN_EXECUTING = 5*60;
    private final long MAX_INTERVAL_BETWEEN_EXECUTING = 2_592_000;
    private final String FORMATTED_MIN_INTERVAL = formatDurationFromSeconds(MIN_INTERVAL_BETWEEN_EXECUTING, true);
    private final String FORMATTED_MAX_INTERVAL = formatDurationFromSeconds(MAX_INTERVAL_BETWEEN_EXECUTING, true);
    private final Instant MAX_DATE_FOR_ONCE_TIMER =  Instant.parse("2036-12-31T23:59:59Z");
    private final int MAX_TIMERS = 30;


    public TimerService(TimerRepository timerRepository, @Lazy CommandRegistry commandRegistry, CommandAccessService commandAccessService, MemberService memberService) {
        this.timerRepository = timerRepository;
        this.commandRegistry = commandRegistry;
        this.commandAccessService = commandAccessService;
        this.memberService = memberService;
    }

    public int getMaxTimers() {
        return MAX_TIMERS;
    }

    @Transactional
    public TimerEntity createOnceTimer(long chatId, @NonNull LocalDateTime executionDate, @NonNull String fullCommandWithArgs, long fromId){

        Instant now = Instant.now();
        Instant executionInstantDate = executionDate.atZone(ZoneId.of("Europe/Moscow")).toInstant();

        if(executionInstantDate.minusSeconds(MIN_INTERVAL_BETWEEN_EXECUTING).isBefore(now)){
            throw new TimerDateOutOfBoundsException("Создать можно только таймер с датой, которая будет минимум через %s от текущей даты."
                    .formatted(FORMATTED_MIN_INTERVAL));
        }if(executionInstantDate.isAfter(MAX_DATE_FOR_ONCE_TIMER)){
            throw new TimerDateOutOfBoundsException("Вы ввели дату, которая находится слишком далеко в будущем. Выберите более раннюю дату.");
        }
        checkTimersLimitAndCommandUsageAbility(chatId, fromId, fullCommandWithArgs);
        return timerRepository.save(
                new TimerEntity(chatId, fromId, TimerType.ONCE, fullCommandWithArgs.trim(), now, executionInstantDate)
        );

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

        return timerRepository.save(
                new TimerEntity(chatId, fromId, TimerType.EACH, fullCommandWithArgs.trim(), now, intervalInSeconds, nextExecution)
        );

    }
    @Transactional
    public TimerEntity createDailyTimer(long chatId, @NonNull LocalTime dailyTime, @NonNull String fullCommandWithArgs, long fromId){
        checkTimersLimitAndCommandUsageAbility(chatId, fromId, fullCommandWithArgs);

        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Europe/Moscow"));   // время чата сейчас
        ZonedDateTime next = now.with(dailyTime); //  текущая дата, но с нужным временем
        if (next.isBefore(now)) {
            next = next.plusDays(1); // если время уже прошло сегодня, то нужно брать завтра
        }
        Instant nextExecution = next.toInstant();
        return timerRepository.save(
                new TimerEntity(chatId, fromId, TimerType.DAILY, fullCommandWithArgs.trim(), now.toInstant(), nextExecution)
        );

    }

    public List<TimerEntity> getAllChatTimersSortedByIdAsc(long chatId){
        return timerRepository.findByChatIdOrderByIdAsc(chatId);
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





}
