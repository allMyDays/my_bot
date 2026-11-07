package com.example.my_bot.unit.service.timer;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.command.CommandRegistry;
import com.example.my_bot.entity.TimerEntity;
import com.example.my_bot.enumeration.TimeZoneType;
import com.example.my_bot.enumeration.timer.TimerType;
import com.example.my_bot.exception.command.CommandAccessDeniedException;
import com.example.my_bot.exception.command.CommandArgumentTooLongException;
import com.example.my_bot.exception.command.CommandInitAnnotationAbsentsException;
import com.example.my_bot.exception.command.UserCommandNotFoundException;
import com.example.my_bot.exception.timer.*;
import com.example.my_bot.repository.TimerRepository;
import com.example.my_bot.service.command.CommandAccessService;
import com.example.my_bot.service.MemberService;
import com.example.my_bot.service.chat.ChatService;
import com.example.my_bot.service.timer.TimerExecutionService;
import com.example.my_bot.service.timer.TimerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import static org.assertj.core.api.Assertions.within;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TimerServiceTest {

    @Mock
    private TimerRepository timerRepository;

    @Mock
    private CommandAccessService commandAccessService;

    @Mock
    private MemberService memberService;

    @Mock
    private TimerExecutionService timerExecutionService;

    @Mock
    private ChatService chatService;

    @Mock
    private CommandRegistry commandRegistry;

    @InjectMocks
    private TimerService timerService;

    private final long chatId = 1L;
    private final long fromId = 100L;
    private final String fullCommand = "/remind me to test";
    private final String userCommand = "/remind";
    private final ZoneOffset chatOffset = ZoneOffset.ofHours(3);
    private final TimeZoneType chatTimeZone = TimeZoneType.GMT_PLUS_3;

    @BeforeEach
    void setUp() {
        timerService.setCommandRegistry(commandRegistry);
        given(chatService.getChatTimeZone(anyLong())).willReturn(chatTimeZone);
    }

    private void mockCommandAccess(boolean granted) {
        mockCommandAnnotation(true);
        lenient().when(memberService.getMemberRolePriority(chatId, fromId)).thenReturn(1);
        lenient().when(commandAccessService.checkCommandAuthorization(chatId, userCommand, 1, fromId)).thenReturn(granted);
    }

    private void mockCommandAnnotation(boolean eventable) {
        Command mockAnnotation = mock(Command.class);
        lenient().when(mockAnnotation.eventable()).thenReturn(eventable);
        lenient().when(mockAnnotation.mainCommandName()).thenReturn(userCommand);
        lenient().when(commandRegistry.getCommandAnnotation(userCommand)).thenReturn(Optional.of(mockAnnotation));
    }


    @Nested
    class CreateOnceTimerTest {

        @Test
        void shouldCreateOnceTimerSuccessfully() {
            LocalDateTime executionDate = LocalDateTime.now().plusHours(2);
            Instant expectedInstant = executionDate.atZone(chatOffset).toInstant();

            given(timerRepository.countByChatId(chatId)).willReturn(5L);
            mockCommandAccess(true);

            TimerEntity savedTimer = new TimerEntity(chatId, fromId, TimerType.ONCE, "remind me to test", expectedInstant);
            given(timerRepository.save(any(TimerEntity.class))).willReturn(savedTimer);

            TimerEntity result = timerService.createOnceTimer(chatId, executionDate, fullCommand, fromId);

            assertThat(result).isSameAs(savedTimer);

            ArgumentCaptor<TimerEntity> captor = ArgumentCaptor.forClass(TimerEntity.class);
            verify(timerRepository).save(captor.capture());
            TimerEntity captured = captor.getValue();

            assertThat(captured.getChatId()).isEqualTo(chatId);
            assertThat(captured.getCreatorId()).isEqualTo(fromId);
            assertThat(captured.getType()).isEqualTo(TimerType.ONCE);
            assertThat(captured.getNextExecution()).isEqualTo(expectedInstant);

            verify(timerExecutionService).putTimerToSchedulerIfExecutionIsNear(savedTimer);
        }


        @Test
        void shouldThrowWhenExecutionDateTooFar() {
            LocalDateTime executionDate = LocalDateTime.of(2050, 1, 1, 0, 0);
            assertThatThrownBy(() -> timerService.createOnceTimer(chatId, executionDate, fullCommand, fromId))
                    .isInstanceOf(TimerDateOutOfBoundsException.class)
                    .hasMessageContaining("слишком далеко в будущем");
        }

        @Test
        void shouldThrowWhenTooManyTimers() {
            given(timerRepository.countByChatId(chatId)).willReturn(30L);
            LocalDateTime executionDate = LocalDateTime.now().plusHours(2);
            assertThatThrownBy(() -> timerService.createOnceTimer(chatId, executionDate, fullCommand, fromId))
                    .isInstanceOf(TooManyTimersException.class);
        }

        @Test
        void shouldThrowWhenCommandArgumentTooLong() {
            given(timerRepository.countByChatId(chatId)).willReturn(5L);
            String longCommand = "/remind " + "a".repeat(101);
            LocalDateTime executionDate = LocalDateTime.now().plusHours(2);
            assertThatThrownBy(() -> timerService.createOnceTimer(chatId, executionDate, longCommand, fromId))
                    .isInstanceOf(CommandArgumentTooLongException.class);
        }

        @Test
        void shouldThrowWhenCommandNotRegistered() {
            given(timerRepository.countByChatId(chatId)).willReturn(5L);
            given(commandRegistry.getCommandAnnotation(userCommand)).willReturn(Optional.empty());
            LocalDateTime executionDate = LocalDateTime.now().plusHours(2);
            assertThatThrownBy(() -> timerService.createOnceTimer(chatId, executionDate, fullCommand, fromId))
                    .isInstanceOf(UserCommandNotFoundException.class);
        }

        @Test
        void shouldThrowWhenCommandNotEventable() {
            given(timerRepository.countByChatId(chatId)).willReturn(5L);
            Command mockAnnotation = mock(Command.class);
            given(mockAnnotation.eventable()).willReturn(false);
            given(mockAnnotation.mainCommandName()).willReturn(userCommand);
            given(commandRegistry.getCommandAnnotation(userCommand)).willReturn(Optional.of(mockAnnotation));
            LocalDateTime executionDate = LocalDateTime.now().plusHours(2);
            assertThatThrownBy(() -> timerService.createOnceTimer(chatId, executionDate, fullCommand, fromId))
                    .isInstanceOf(CannotUseThisCommandForTimerException.class);
        }

        @Test
        void shouldThrowWhenAccessDenied() {
            given(timerRepository.countByChatId(chatId)).willReturn(5L);
            Command mockAnnotation = mock(Command.class);
            given(mockAnnotation.eventable()).willReturn(true);
            given(mockAnnotation.mainCommandName()).willReturn(userCommand);
            given(commandRegistry.getCommandAnnotation(userCommand)).willReturn(Optional.of(mockAnnotation));
            given(memberService.getMemberRolePriority(chatId, fromId)).willReturn(1);
            given(commandAccessService.checkCommandAuthorization(chatId, userCommand, 1, fromId)).willReturn(false);

            LocalDateTime executionDate = LocalDateTime.now().plusHours(2);
            assertThatThrownBy(() -> timerService.createOnceTimer(chatId, executionDate, fullCommand, fromId))
                    .isInstanceOf(CommandAccessDeniedException.class);
        }
    }


    @Nested
    class CreateEachTimerTest {

        @Test
        void shouldCreateEachTimerSuccessfully() {
            long interval = 600;
            Instant now = Instant.now();
            Instant expectedNext = now.plusSeconds(interval);

            given(timerRepository.countByChatId(chatId)).willReturn(5L);
            mockCommandAccess(true);

            TimerEntity savedTimer = new TimerEntity(chatId, fromId, TimerType.EACH, "remind me to test", interval, expectedNext);
            given(timerRepository.save(any(TimerEntity.class))).willReturn(savedTimer);

            TimerEntity result = timerService.createEachTimer(chatId, interval, fullCommand, fromId);

            assertThat(result).isSameAs(savedTimer);

            ArgumentCaptor<TimerEntity> captor = ArgumentCaptor.forClass(TimerEntity.class);
            verify(timerRepository).save(captor.capture());
            TimerEntity captured = captor.getValue();

            assertThat(captured.getChatId()).isEqualTo(chatId);
            assertThat(captured.getCreatorId()).isEqualTo(fromId);
            assertThat(captured.getType()).isEqualTo(TimerType.EACH);
            assertThat(captured.getIntervalSeconds()).isEqualTo(interval);
            assertThat(captured.getNextExecution()).isAfter(now);

            verify(timerExecutionService).putTimerToSchedulerIfExecutionIsNear(savedTimer);
        }

        @Test
        void shouldThrowWhenIntervalTooShort() {
            long interval = 60;
            assertThatThrownBy(() -> timerService.createEachTimer(chatId, interval, fullCommand, fromId))
                    .isInstanceOf(TimerIntervalOutOfBoundsException.class)
                    .hasMessageContaining("чаще чем раз в");
        }

        @Test
        void shouldThrowWhenIntervalTooLong() {
            long interval = 2_592_001;
            assertThatThrownBy(() -> timerService.createEachTimer(chatId, interval, fullCommand, fromId))
                    .isInstanceOf(TimerIntervalOutOfBoundsException.class)
                    .hasMessageContaining("Максимальный интервал");
        }

        @Test
        void shouldThrowWhenTooManyTimers() {
            given(timerRepository.countByChatId(chatId)).willReturn(30L);
            long interval = 600;
            assertThatThrownBy(() -> timerService.createEachTimer(chatId, interval, fullCommand, fromId))
                    .isInstanceOf(TooManyTimersException.class);
        }
    }


    @Nested
    class CreateDailyTimerTest {

        @Test
        void shouldCreateDailyTimerForTodayIfTimeNotPassed() {
            LocalTime dailyTime = LocalTime.now(ZoneId.from(chatOffset)).plusHours(1);
            ZonedDateTime now = ZonedDateTime.now(chatOffset);
            ZonedDateTime expectedNext = now.with(dailyTime);
            Instant expectedInstant = expectedNext.toInstant();

            given(timerRepository.countByChatId(chatId)).willReturn(5L);
            mockCommandAccess(true);

            TimerEntity savedTimer = new TimerEntity(chatId, fromId, TimerType.DAILY, "remind me to test", expectedInstant);
            given(timerRepository.save(any(TimerEntity.class))).willReturn(savedTimer);

            TimerEntity result = timerService.createDailyTimer(chatId, dailyTime, fullCommand, fromId);

            assertThat(result).isSameAs(savedTimer);

            ArgumentCaptor<TimerEntity> captor = ArgumentCaptor.forClass(TimerEntity.class);
            verify(timerRepository).save(captor.capture());
            TimerEntity captured = captor.getValue();

            assertThat(captured.getType()).isEqualTo(TimerType.DAILY);
            assertThat(captured.getNextExecution()).isEqualTo(expectedInstant);

            verify(timerExecutionService).putTimerToSchedulerIfExecutionIsNear(savedTimer);
        }

        @Test
        void shouldCreateDailyTimerForTomorrowIfTimePassed() {
            LocalTime dailyTime = LocalTime.now(ZoneId.from(chatOffset)).minusHours(1);
            ZonedDateTime now = ZonedDateTime.now(chatOffset);
            ZonedDateTime expectedNext = now.with(dailyTime).plusDays(1);
            Instant expectedInstant = expectedNext.toInstant();

            given(timerRepository.countByChatId(chatId)).willReturn(5L);
            mockCommandAccess(true);

            TimerEntity savedTimer = new TimerEntity(chatId, fromId, TimerType.DAILY, "remind me to test", expectedInstant);
            given(timerRepository.save(any(TimerEntity.class))).willReturn(savedTimer);

            timerService.createDailyTimer(chatId, dailyTime, fullCommand, fromId);

            ArgumentCaptor<TimerEntity> captor = ArgumentCaptor.forClass(TimerEntity.class);
            verify(timerRepository).save(captor.capture());
            TimerEntity captured = captor.getValue();
            assertThat(captured.getNextExecution()).isEqualTo(expectedInstant);
        }
    }

    @Test
    void shouldDeleteTimerById() {
        long timerId = 42L;
        timerService.deleteTimerById(timerId);
        verify(timerExecutionService).cancelTaskAndRemoveFromCache(timerId);
        verify(timerRepository).deleteById(timerId);
    }


    @Nested
    class IncrementNextExecutionTest {

        @Test
        void shouldThrowWhenTimerNotFound() {
            long timerId = 1L;
            given(timerRepository.findById(timerId)).willReturn(Optional.empty());
            assertThatThrownBy(() -> timerService.incrementNextExecutionAndExecutionCounter(timerId))
                    .isInstanceOf(TimerNotFoundException.class);
        }

        @Test
        void shouldThrowWhenTimerTypeIsOnce() {
            long timerId = 1L;
            TimerEntity timer = new TimerEntity();
            timer.setType(TimerType.ONCE);
            given(timerRepository.findById(timerId)).willReturn(Optional.of(timer));
            assertThatThrownBy(() -> timerService.incrementNextExecutionAndExecutionCounter(timerId))
                    .isInstanceOf(IllegalTimerTypeException.class);
        }

        @Test
        void shouldThrowWhenSystemLimitReached() {
            long timerId = 1L;
            TimerEntity timer = new TimerEntity();
            timer.setType(TimerType.EACH);
            timer.setExecutionCounter(99);
            timer.setIntervalSeconds(600L);
            given(timerRepository.findById(timerId)).willReturn(Optional.of(timer));
            assertThatThrownBy(() -> timerService.incrementNextExecutionAndExecutionCounter(timerId))
                    .isInstanceOf(TimerHasReachedExecutionLimitException.class);
        }

        @Test
        void shouldThrowWhenCustomLimitReached() {
            long timerId = 1L;
            TimerEntity timer = new TimerEntity();
            timer.setType(TimerType.EACH);
            timer.setExecutionCounter(4);
            timer.setCustomExecutionLimit(5);
            timer.setIntervalSeconds(600L);
            given(timerRepository.findById(timerId)).willReturn(Optional.of(timer));
            assertThatThrownBy(() -> timerService.incrementNextExecutionAndExecutionCounter(timerId))
                    .isInstanceOf(TimerHasReachedExecutionLimitException.class);
        }

        @Test
        void shouldIncrementForEachTimer() {
            long timerId = 1L;
            TimerEntity timer = new TimerEntity();
            timer.setType(TimerType.EACH);
            timer.setExecutionCounter(0);
            timer.setIntervalSeconds(600L);
            timer.setNextExecution(Instant.now().minusSeconds(100));
            given(timerRepository.findById(timerId)).willReturn(Optional.of(timer));

            Instant now = Instant.now();
            Instant expectedNew = now.plusSeconds(600);

            Instant result = timerService.incrementNextExecutionAndExecutionCounter(timerId);

            assertThat(result).isCloseTo(expectedNew, within(1, ChronoUnit.SECONDS));
            assertThat(timer.getExecutionCounter()).isEqualTo(1);
            assertThat(timer.getNextExecution()).isCloseTo(expectedNew, within(1, ChronoUnit.SECONDS));
        }

        @Test
        void shouldIncrementForDailyTimerAndCatchUp() {
            long timerId = 1L;
            TimerEntity timer = new TimerEntity();
            timer.setType(TimerType.DAILY);
            timer.setExecutionCounter(0);
            Instant oldNext = Instant.now().minus(1, ChronoUnit.DAYS);
            timer.setNextExecution(oldNext);
            given(timerRepository.findById(timerId)).willReturn(Optional.of(timer));

            Instant now = Instant.now();
            Instant expected = oldNext.plus(1, ChronoUnit.DAYS);
            while (expected.isBefore(now) || expected.equals(now)) {
                expected = expected.plus(1, ChronoUnit.DAYS);
            }

            Instant result = timerService.incrementNextExecutionAndExecutionCounter(timerId);

            assertThat(result).isCloseTo(expected, within(1, ChronoUnit.SECONDS));
            assertThat(timer.getExecutionCounter()).isEqualTo(1);
            assertThat(timer.getNextExecution()).isCloseTo(expected, within(1, ChronoUnit.SECONDS));
        }
    }

    @Nested
    class SetCustomExecutionLimitTest {

        @Test
        void shouldThrowWhenTimerNotFound() {
            long timerId = 1L;
            given(timerRepository.findById(timerId)).willReturn(Optional.empty());
            assertThatThrownBy(() -> timerService.setCustomExecutionLimit(timerId, 10))
                    .isInstanceOf(TimerNotFoundException.class);
        }

        @Test
        void shouldThrowWhenTimerTypeOnce() {
            long timerId = 1L;
            TimerEntity timer = new TimerEntity();
            timer.setType(TimerType.ONCE);
            given(timerRepository.findById(timerId)).willReturn(Optional.of(timer));
            assertThatThrownBy(() -> timerService.setCustomExecutionLimit(timerId, 10))
                    .isInstanceOf(IllegalTimerTypeException.class);
        }

        @Test
        void shouldThrowWhenLimitAlreadySame() {
            long timerId = 1L;
            TimerEntity timer = new TimerEntity();
            timer.setType(TimerType.EACH);
            timer.setCustomExecutionLimit(10);
            given(timerRepository.findById(timerId)).willReturn(Optional.of(timer));
            assertThatThrownBy(() -> timerService.setCustomExecutionLimit(timerId, 10))
                    .isInstanceOf(TimerAlreadyHasThatExecutionLimitException.class);
        }

        @Test
        void shouldThrowWhenLimitExceedsSystemMax() {
            long timerId = 1L;
            TimerEntity timer = new TimerEntity();
            timer.setType(TimerType.EACH);
            given(timerRepository.findById(timerId)).willReturn(Optional.of(timer));
            assertThatThrownBy(() -> timerService.setCustomExecutionLimit(timerId, 200))
                    .isInstanceOf(IncorrectTimerExecutionLimitException.class)
                    .hasMessageContaining("Максимальный лимит");
        }

        @Test
        void shouldThrowWhenLimitLessOrEqualCurrentExecutionCounter() {
            long timerId = 1L;
            TimerEntity timer = new TimerEntity();
            timer.setType(TimerType.EACH);
            timer.setExecutionCounter(5);
            given(timerRepository.findById(timerId)).willReturn(Optional.of(timer));
            assertThatThrownBy(() -> timerService.setCustomExecutionLimit(timerId, 5))
                    .isInstanceOf(IncorrectTimerExecutionLimitException.class)
                    .hasMessageContaining("уже успел выполниться");
        }

        @Test
        void shouldSetCustomLimitSuccessfully() {
            long timerId = 1L;
            TimerEntity timer = new TimerEntity();
            timer.setType(TimerType.EACH);
            timer.setExecutionCounter(2);
            timer.setCustomExecutionLimit(null);
            given(timerRepository.findById(timerId)).willReturn(Optional.of(timer));

            timerService.setCustomExecutionLimit(timerId, 10);

            assertThat(timer.getCustomExecutionLimit()).isEqualTo(10);
        }
    }

    @Nested
    class ChangeNextExecutionForEachTimerTest {

        @Test
        void shouldThrowWhenTimerNotFound() {
            long timerId = 1L;
            given(timerRepository.findById(timerId)).willReturn(Optional.empty());
            LocalDateTime newTime = LocalDateTime.now().plusHours(2);
            assertThatThrownBy(() -> timerService.changeNextExecutionForEachTimer(timerId, newTime))
                    .isInstanceOf(TimerNotFoundException.class);
        }

        @Test
        void shouldThrowWhenTimerNotEach() {
            long timerId = 1L;
            TimerEntity timer = new TimerEntity();
            timer.setType(TimerType.DAILY);
            given(timerRepository.findById(timerId)).willReturn(Optional.of(timer));
            LocalDateTime newTime = LocalDateTime.now().plusHours(2);
            assertThatThrownBy(() -> timerService.changeNextExecutionForEachTimer(timerId, newTime))
                    .isInstanceOf(IllegalTimerTypeException.class);
        }

        @Test
        void shouldThrowWhenNewTimeSameAsCurrent() {
            long timerId = 1L;
            TimerEntity timer = new TimerEntity();
            timer.setType(TimerType.EACH);
            timer.setChatId(chatId);
            Instant now = Instant.now();
            timer.setNextExecution(now);
            given(timerRepository.findById(timerId)).willReturn(Optional.of(timer));
            LocalDateTime newTime = LocalDateTime.ofInstant(now, chatOffset);
            assertThatThrownBy(() -> timerService.changeNextExecutionForEachTimer(timerId, newTime))
                    .isInstanceOf(TimerAlreadyHasThatNextExecutionException.class);
        }

        @Test
        void shouldThrowWhenNewTimeOutOfBounds() {
            long timerId = 1L;
            TimerEntity timer = new TimerEntity();
            timer.setType(TimerType.EACH);
            timer.setChatId(chatId);
            timer.setNextExecution(Instant.now().plus(1, ChronoUnit.HOURS));
            given(timerRepository.findById(timerId)).willReturn(Optional.of(timer));
            // Создаём время через 30 секунд от текущего момента UTC, затем переводим в LocalDateTime с часовым поясом чата
            Instant tooSoonInstant = Instant.now().plusSeconds(30);
            LocalDateTime newTime = LocalDateTime.ofInstant(tooSoonInstant, chatOffset);
            assertThatThrownBy(() -> timerService.changeNextExecutionForEachTimer(timerId, newTime))
                    .isInstanceOf(TimerDateOutOfBoundsException.class);
        }

        @Test
        void shouldChangeNextExecutionSuccessfully() {
            long timerId = 1L;
            TimerEntity timer = new TimerEntity();
            timer.setType(TimerType.EACH);
            timer.setChatId(chatId);
            timer.setNextExecution(Instant.now().plus(1, ChronoUnit.HOURS));
            given(timerRepository.findById(timerId)).willReturn(Optional.of(timer));

            LocalDateTime newTime = LocalDateTime.now().plusHours(3);
            Instant newInstant = newTime.atZone(chatOffset).toInstant();

            timerService.changeNextExecutionForEachTimer(timerId, newTime);

            assertThat(timer.getNextExecution()).isEqualTo(newInstant);
            verify(timerExecutionService).cancelTaskAndRemoveFromCache(timerId);
            verify(timerExecutionService).putTimerToSchedulerIfExecutionIsNear(timer);
        }
    }

    @Test
    void shouldReturnTimersForChat() {
        List<TimerEntity> expected = List.of(new TimerEntity(), new TimerEntity());
        given(timerRepository.findByChatIdOrderByIdAsc(chatId)).willReturn(expected);
        List<TimerEntity> result = timerService.getChatTimersSortedByIdAsc(chatId);
        assertThat(result).isSameAs(expected);
    }

    @Nested
    class GetAllTimersWithNextExecutionLessThanTest {

        @Test
        void shouldReturnAllWhenExcludedNull() {
            Instant required = Instant.now().plusSeconds(60);
            List<TimerEntity> expected = List.of(new TimerEntity());
            given(timerRepository.findAllTimersWithNextExecutionLessThan(required)).willReturn(expected);
            List<TimerEntity> result = timerService.getAllTimersWithNextExecutionLessThan(required, null);
            assertThat(result).isSameAs(expected);
            verify(timerRepository, never()).findAllTimersWithNextExecutionLessThan(any(), anySet());
        }

        @Test
        void shouldReturnAllWhenExcludedEmpty() {
            Instant required = Instant.now().plusSeconds(60);
            List<TimerEntity> expected = List.of(new TimerEntity());
            given(timerRepository.findAllTimersWithNextExecutionLessThan(required)).willReturn(expected);
            List<TimerEntity> result = timerService.getAllTimersWithNextExecutionLessThan(required, Collections.emptySet());
            assertThat(result).isSameAs(expected);
            verify(timerRepository, never()).findAllTimersWithNextExecutionLessThan(any(), anySet());
        }

        @Test
        void shouldReturnFilteredWhenExcludedProvided() {
            Instant required = Instant.now().plusSeconds(60);
            Set<Long> excluded = Set.of(1L, 2L);
            List<TimerEntity> expected = List.of(new TimerEntity());
            given(timerRepository.findAllTimersWithNextExecutionLessThan(required, excluded)).willReturn(expected);
            List<TimerEntity> result = timerService.getAllTimersWithNextExecutionLessThan(required, excluded);
            assertThat(result).isSameAs(expected);
            verify(timerRepository, never()).findAllTimersWithNextExecutionLessThan(required);
        }
    }
}