package com.example.my_bot.unit.service.timer;


import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.CommandDispatcher;
import com.example.my_bot.config.CaffeineCacheManager;
import com.example.my_bot.dto.chat.ChatDetailsDto;
import com.example.my_bot.dto.command.CommandRoutingData;
import com.example.my_bot.dto.submanager.SubmanagerDto;
import com.example.my_bot.entity.TimerEntity;

import com.example.my_bot.enumeration.timer.TimerType;
import com.example.my_bot.exception.timer.TimerHasReachedExecutionLimitException;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.service.chat.ChatService;
import com.example.my_bot.service.submanager.SubmanagerService;
import com.example.my_bot.service.timer.TimerExecutionService;
import com.example.my_bot.service.timer.TimerService;
import com.github.benmanes.caffeine.cache.Cache;
import com.vk.api.sdk.client.actors.GroupActor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TimerExecutionServiceTest {

    @Mock
    private TimerService timerService;

    @Mock
    private CommandDispatcher commandDispatcher;

    @Mock
    private MessageMapper messageMapper;

    @Mock
    private CaffeineCacheManager cacheManager;

    @Mock
    private VkChatClient vkChatClient;

    @Mock
    private ChatService chatService;

    @Mock
    private SubmanagerService submanagerService;

    @Mock
    private GroupActor theMainBotGroupActor;

    @Mock
    private Cache<Long, ScheduledFuture<?>> timerTasksCache;

    @Mock
    private ScheduledThreadPoolExecutor scheduler;

    @InjectMocks
    private TimerExecutionService timerExecutionService;

    private final long chatId = 1L;
    private final long timerId = 100L;
    private final long creatorId = 200L;
    private final String fullCommand = "/remind test";

    @BeforeEach
    void setUp() throws Exception {
        Field schedulerField = TimerExecutionService.class.getDeclaredField("scheduler");
        schedulerField.setAccessible(true);
        schedulerField.set(timerExecutionService, scheduler);

        timerExecutionService.setTimerService(timerService);
        timerExecutionService.setCommandDispatcher(commandDispatcher);

        given(cacheManager.getTimerTasksCache()).willReturn(timerTasksCache);
        given(timerTasksCache.asMap()).willReturn(new java.util.concurrent.ConcurrentHashMap<>());
    }

    @Test
    void shouldScheduleIfExecutionIsNear() {
        TimerEntity timer = new TimerEntity(chatId, creatorId, TimerType.ONCE, fullCommand, Instant.now().plusSeconds(60));
        timer.setId(timerId);
        given(timerTasksCache.getIfPresent(timerId)).willReturn(null);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        doReturn(future).when(scheduler).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));

        timerExecutionService.putTimerToSchedulerIfExecutionIsNear(timer);

        verify(scheduler).schedule(any(Runnable.class), anyLong(), eq(TimeUnit.SECONDS));
        verify(timerTasksCache).put(eq(timerId), any(ScheduledFuture.class));
    }

    @Test
    void shouldNotScheduleIfExecutionIsFar() {
        TimerEntity timer = new TimerEntity(chatId, creatorId, TimerType.ONCE, fullCommand, Instant.now().plusSeconds(60 * 60));
        timer.setId(timerId);
        timerExecutionService.putTimerToSchedulerIfExecutionIsNear(timer);
        verify(scheduler, never()).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
        verify(timerTasksCache, never()).put(anyLong(), any());
    }

    @Test
    void shouldCancelAndRemoveIfFutureExists() {
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        doReturn(future).when(timerTasksCache).getIfPresent(timerId);
        timerExecutionService.cancelTaskAndRemoveFromCache(timerId);
        verify(future).cancel(false);
        verify(timerTasksCache).invalidate(timerId);
    }

    @Test
    void shouldDoNothingIfFutureNotExists() {
        given(timerTasksCache.getIfPresent(timerId)).willReturn(null);
        timerExecutionService.cancelTaskAndRemoveFromCache(timerId);
        verify(timerTasksCache, never()).invalidate(anyLong());
    }

    @Test
    void shouldExecuteOnceTimerSuccessfully() throws Exception {
        TimerEntity timer = new TimerEntity(chatId, creatorId, TimerType.ONCE, fullCommand, Instant.now());
        timer.setId(timerId);
        ChatDetailsDto chatDetails = new ChatDetailsDto();
        chatDetails.setBoundSubmanagerId(null);
        given(chatService.getCachedChatDetails(chatId, false)).willReturn(chatDetails);

        given(messageMapper.toCommandMessageDto(any(CommandRoutingData.class), anyLong(), anyString(), anyInt(), anyBoolean(), anyBoolean()))
                .willReturn(null);
        doNothing().when(commandDispatcher).dispatch(any());

        Method method = TimerExecutionService.class.getDeclaredMethod("generateTask", TimerEntity.class);
        method.setAccessible(true);
        Runnable task = (Runnable) method.invoke(timerExecutionService, timer);
        task.run();

        verify(commandDispatcher).dispatch(any());
        verify(timerService).deleteTimerById(timerId);
        verify(timerService, never()).incrementNextExecutionAndExecutionCounter(anyLong());
        verify(vkChatClient, never()).sendText(any());
    }

    @Test
    void shouldHandleDispatchException() throws Exception {
        TimerEntity timer = new TimerEntity(chatId, creatorId, TimerType.ONCE, fullCommand, Instant.now());
        timer.setId(timerId);
        ChatDetailsDto chatDetails = new ChatDetailsDto();
        chatDetails.setBoundSubmanagerId(null);
        given(chatService.getCachedChatDetails(chatId, false)).willReturn(chatDetails);

        given(messageMapper.toCommandMessageDto(any(), anyLong(), anyString(), anyInt(), anyBoolean(), anyBoolean()))
                .willReturn(null);
        doThrow(new RuntimeException("Dispatch error")).when(commandDispatcher).dispatch(any());

        doNothing().when(timerService).deleteTimerById(timerId);
        given(messageMapper.toSendMessageDto(anyString(), any(CommandRoutingData.class)))
                .willReturn(null);
        when(vkChatClient.sendText(any())).thenReturn(Collections.emptyList()); // исправлено

        Method method = TimerExecutionService.class.getDeclaredMethod("generateTask", TimerEntity.class);
        method.setAccessible(true);
        Runnable task = (Runnable) method.invoke(timerExecutionService, timer);
        task.run();

        verify(timerService).deleteTimerById(timerId);
        verify(vkChatClient).sendText(any());
        verify(timerService, never()).incrementNextExecutionAndExecutionCounter(anyLong());
    }

    @Test
    void shouldHandleTimerHasReachedExecutionLimitException() throws Exception {
        TimerEntity timer = new TimerEntity(chatId, creatorId, TimerType.EACH, fullCommand, 60L, Instant.now());
        timer.setId(timerId);
        ChatDetailsDto chatDetails = new ChatDetailsDto();
        chatDetails.setBoundSubmanagerId(null);
        given(chatService.getCachedChatDetails(chatId, false)).willReturn(chatDetails);

        given(messageMapper.toCommandMessageDto(any(), anyLong(), anyString(), anyInt(), anyBoolean(), anyBoolean()))
                .willReturn(null);
        doNothing().when(commandDispatcher).dispatch(any());

        given(timerService.incrementNextExecutionAndExecutionCounter(timerId))
                .willThrow(new TimerHasReachedExecutionLimitException());

        doNothing().when(timerService).deleteTimerById(timerId);
        given(messageMapper.toSendMessageDto(anyString(), any(CommandRoutingData.class)))
                .willReturn(null);
        when(vkChatClient.sendText(any())).thenReturn(Collections.emptyList());

        Method method = TimerExecutionService.class.getDeclaredMethod("generateTask", TimerEntity.class);
        method.setAccessible(true);
        Runnable task = (Runnable) method.invoke(timerExecutionService, timer);
        task.run();

        verify(timerService).incrementNextExecutionAndExecutionCounter(timerId);
        verify(timerService).deleteTimerById(timerId);
        verify(vkChatClient).sendText(any());
    }

    @Test
    void shouldCancelTimerIfNextExecutionIsFar() throws Exception {
        TimerEntity timer = new TimerEntity(chatId, creatorId, TimerType.EACH, fullCommand, 60L, Instant.now());
        timer.setId(timerId);
        ChatDetailsDto chatDetails = new ChatDetailsDto();
        chatDetails.setBoundSubmanagerId(null);
        given(chatService.getCachedChatDetails(chatId, false)).willReturn(chatDetails);

        given(messageMapper.toCommandMessageDto(any(), anyLong(), anyString(), anyInt(), anyBoolean(), anyBoolean()))
                .willReturn(null);
        doNothing().when(commandDispatcher).dispatch(any());

        Instant farFuture = Instant.now().plus(13, java.time.temporal.ChronoUnit.HOURS);
        given(timerService.incrementNextExecutionAndExecutionCounter(timerId))
                .willReturn(farFuture);

        given(timerTasksCache.getIfPresent(timerId)).willReturn(null);

        Method method = TimerExecutionService.class.getDeclaredMethod("generateTask", TimerEntity.class);
        method.setAccessible(true);
        Runnable task = (Runnable) method.invoke(timerExecutionService, timer);
        task.run();

        verify(timerTasksCache).getIfPresent(timerId);
        verify(timerService).incrementNextExecutionAndExecutionCounter(timerId);
    }

    @Test
    void shouldHandleOtherExceptionDuringUpdate() throws Exception {
        TimerEntity timer = new TimerEntity(chatId, creatorId, TimerType.EACH, fullCommand, 60L, Instant.now());
        timer.setId(timerId);
        ChatDetailsDto chatDetails = new ChatDetailsDto();
        chatDetails.setBoundSubmanagerId(null);
        given(chatService.getCachedChatDetails(chatId, false)).willReturn(chatDetails);

        given(messageMapper.toCommandMessageDto(any(), anyLong(), anyString(), anyInt(), anyBoolean(), anyBoolean()))
                .willReturn(null);
        doNothing().when(commandDispatcher).dispatch(any());

        given(timerService.incrementNextExecutionAndExecutionCounter(timerId))
                .willThrow(new RuntimeException("Update error"));

        doNothing().when(timerService).deleteTimerById(timerId);
        given(messageMapper.toSendMessageDto(anyString(), any(CommandRoutingData.class)))
                .willReturn(null);
        when(vkChatClient.sendText(any())).thenReturn(Collections.emptyList());

        Method method = TimerExecutionService.class.getDeclaredMethod("generateTask", TimerEntity.class);
        method.setAccessible(true);
        Runnable task = (Runnable) method.invoke(timerExecutionService, timer);
        task.run();

        verify(timerService).deleteTimerById(timerId);
        verify(vkChatClient).sendText(any());
    }

    @Test
    void shouldHandleSubmanagerLogic() throws Exception {
        long submanagerId = 999L;
        long submanagerChatId = 777L;

        TimerEntity timer = new TimerEntity(chatId, creatorId, TimerType.ONCE, fullCommand, Instant.now());
        timer.setId(timerId);
        ChatDetailsDto chatDetails = new ChatDetailsDto();
        chatDetails.setBoundSubmanagerId(submanagerId);
        given(chatService.getCachedChatDetails(chatId, false)).willReturn(chatDetails);

        SubmanagerDto submanagerDto = new SubmanagerDto(submanagerId, "token", 1, "secret");
        given(submanagerService.getSubmanagerOrThrowIfAbsents(submanagerId)).willReturn(submanagerDto);
        given(chatService.getSubmanagerChatIdByMainChatId(submanagerId, chatId)).willReturn(submanagerChatId);

        given(messageMapper.toCommandMessageDto(any(CommandRoutingData.class), anyLong(), anyString(), anyInt(), anyBoolean(), anyBoolean()))
                .willReturn(null);
        doNothing().when(commandDispatcher).dispatch(any());

        Method method = TimerExecutionService.class.getDeclaredMethod("generateTask", TimerEntity.class);
        method.setAccessible(true);
        Runnable task = (Runnable) method.invoke(timerExecutionService, timer);
        task.run();

        ArgumentCaptor<CommandRoutingData> captor = ArgumentCaptor.forClass(CommandRoutingData.class);
        verify(messageMapper).toCommandMessageDto(captor.capture(), anyLong(), anyString(), anyInt(), anyBoolean(), anyBoolean());
        CommandRoutingData routingData = captor.getValue();
        assertThat(routingData.getExecutorBot().getGroupId()).isEqualTo(submanagerId);
        assertThat(routingData.getVkApiChatId()).isEqualTo(submanagerChatId);
    }

    @Test
    void shouldScheduleOnlyTimersNotInCache() throws Exception {
        ConcurrentMap<Long, ScheduledFuture<?>> realMap = new ConcurrentHashMap<>();
        realMap.put(1L, mock(ScheduledFuture.class));
        realMap.put(2L, mock(ScheduledFuture.class));
        doReturn(realMap).when(timerTasksCache).asMap();

        TimerEntity timer1 = new TimerEntity(chatId, creatorId, TimerType.ONCE, fullCommand, Instant.now());
        timer1.setId(1L);
        TimerEntity timer2 = new TimerEntity(chatId, creatorId, TimerType.ONCE, fullCommand, Instant.now());
        timer2.setId(3L);
        List<TimerEntity> timers = List.of(timer1, timer2);
        given(timerService.getAllTimersWithNextExecutionLessThan(any(Instant.class), eq(Set.of(1L, 2L))))
                .willReturn(timers);

        given(timerTasksCache.getIfPresent(1L)).willReturn(mock(ScheduledFuture.class));

        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        doReturn(future).when(scheduler).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));

        Method method = TimerExecutionService.class.getDeclaredMethod("putAllNearTimersToScheduledExecutor");
        method.setAccessible(true);
        method.invoke(timerExecutionService);

        verify(scheduler).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
        verify(timerTasksCache, never()).put(eq(1L), any());
        verify(timerTasksCache).put(eq(3L), any(ScheduledFuture.class));
    }
}
