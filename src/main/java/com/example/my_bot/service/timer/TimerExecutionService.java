package com.example.my_bot.service.timer;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.CommandDispatcher;
import com.example.my_bot.config.CaffeineCacheManager;
import com.example.my_bot.dto.chat.ChatDetailsDto;
import com.example.my_bot.dto.command.CommandRoutingData;
import com.example.my_bot.dto.submanager.SubmanagerDto;
import com.example.my_bot.entity.TimerEntity;
import com.example.my_bot.exception.timer.TimerHasReachedExecutionLimitException;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.service.submanager.SubmanagerService;
import com.example.my_bot.service.chat.ChatService;
import com.github.benmanes.caffeine.cache.Cache;
import com.vk.api.sdk.client.actors.GroupActor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

import static com.example.my_bot.enumeration.timer.TimerType.*;
import static com.example.my_bot.utils.ChatUtils.convertToPeerId;
import static java.util.concurrent.TimeUnit.DAYS;

@Slf4j
@Service
@RequiredArgsConstructor
public class TimerExecutionService {

    private final static int CORE_POOL_SIZE = 3;
    private final static long MAX_SECONDS_BETWEEN_NOW_AND_EXECUTION = 30*60;
    private final static int CONVERSATION_MESSAGE_ID = 0;
    private final GroupActor theMainBotGroupActor;

    private TimerService timerService;
    private CommandDispatcher commandDispatcher;
    private final MessageMapper messageMapper;
    private final CaffeineCacheManager cacheManager;
    private final ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(CORE_POOL_SIZE);
    private final VkChatClient vkChatClient;
    private final ChatService chatService;
    private final SubmanagerService submanagerService;

    {
        scheduler.setRemoveOnCancelPolicy(true);
    }

    @Autowired
    @Lazy
    public void setTimerService(TimerService timerService) {
        this.timerService = timerService;
    }
    @Autowired
    @Lazy
    public void setCommandDispatcher(CommandDispatcher commandDispatcher) {
        this.commandDispatcher = commandDispatcher;
    }

    public void putTimerToSchedulerIfExecutionIsNear(@NonNull TimerEntity timer){
        if(Duration.between(Instant.now(), timer.getNextExecution()).getSeconds() <= MAX_SECONDS_BETWEEN_NOW_AND_EXECUTION){
            putTimerToScheduler(timer);
        }
    }
    public void cancelTaskAndRemoveFromCache(long timerId){
        ScheduledFuture<?> future = cacheManager.getTimerTasksCache().getIfPresent(timerId);
        if(future==null){
            log.info("timer {} does not have ScheduledFuture link in cache TimerTasksCache", timerId);
        }
        else{
            future.cancel(false);
            cacheManager.getTimerTasksCache().invalidate(timerId);
        }
    }

    private void putTimerToScheduler(@NonNull TimerEntity timer){
        cancelTaskAndRemoveFromCache(timer.getId());
        Runnable task = generateTask(timer);
        long secondsBetweenNowAndExecution = Duration.between(Instant.now(), timer.getNextExecution()).getSeconds();
        ScheduledFuture<?> future = switch (timer.getType()){
            case EACH -> scheduler.scheduleWithFixedDelay(task, secondsBetweenNowAndExecution, timer.getIntervalSeconds(), TimeUnit.SECONDS);
            case DAILY -> scheduler.scheduleWithFixedDelay(task, secondsBetweenNowAndExecution, DAYS.toSeconds(1), TimeUnit.SECONDS);
            case ONCE -> scheduler.schedule(task, secondsBetweenNowAndExecution, TimeUnit.SECONDS);
        };
        cacheManager.getTimerTasksCache().put(timer.getId(), future);

    }
    private Runnable generateTask(@NonNull TimerEntity timer){
        return () -> {
            long dataBaseChatId = timer.getChatId();
            long vkApiChatId = dataBaseChatId;
            long timerId = timer.getId();
            GroupActor executorBot = theMainBotGroupActor;

            CommandRoutingData commandRoutingData = new CommandRoutingData();
            commandRoutingData.setDataBaseChatId(dataBaseChatId);

            ChatDetailsDto currentChat = chatService.getCachedChatDetails(dataBaseChatId, false);
            if(currentChat.getBoundSubmanagerId()!=null){
                SubmanagerDto subInfo = submanagerService.getSubmanagerOrThrowIfAbsents(currentChat.getBoundSubmanagerId());
                vkApiChatId = chatService.getSubmanagerChatIdByMainChatId(currentChat.getBoundSubmanagerId(), dataBaseChatId);
                executorBot = subInfo.getGroupActor();
            }
            commandRoutingData.setOriginalEventPeerId(convertToPeerId(vkApiChatId));
            commandRoutingData.setResponsePeerId(convertToPeerId(vkApiChatId));
            commandRoutingData.setVkApiChatId(vkApiChatId);
            commandRoutingData.setExecutorBot(executorBot);
            commandRoutingData.setResponderBot(executorBot);

            try{
                commandDispatcher.dispatch(
                        messageMapper.toCommandMessageDto(commandRoutingData, timer.getCreatorId(), timer.getFullCommand(), CONVERSATION_MESSAGE_ID,false,true)
                );
            }
            catch (Exception e) {
                log.warn("error while execution timer {} in chat {}, the timer is gonna be deleted.",timerId,dataBaseChatId, e);
                deleteTimerAndSendNotification(
                        timerId, timer.getFullCommand(), dataBaseChatId, "Ваш таймер с командой «%s» завершился с ошибкой, поэтому был удалён.", commandRoutingData
                );
                return;
            }
            try{
                if(timer.getType()==ONCE){
                    timerService.deleteTimerById(timerId); // удаляю одноразовый таймер
                }
                else{     // обновляю дату следующего срабатывания для многоразового таймера
                    Instant newNextExecution = timerService.incrementNextExecutionAndExecutionCounter(timerId);
                    if(Duration.between(Instant.now(), newNextExecution).toHours()>=12){
                        // отменяю многоразовый таймер потому-что следующий вызов только через 12 часов и более
                        cancelTaskAndRemoveFromCache(timerId);
                    }
                }
            }
            catch(TimerHasReachedExecutionLimitException e){
                deleteTimerAndSendNotification(
                        timerId, timer.getFullCommand(), dataBaseChatId, "Ваш таймер с командой «%s» достиг конца своего жизненного цикла и был удалён.", commandRoutingData
                );

            }
            catch (Exception e){
                log.error("error updating timer {} info after it's just been executed in chat {}: ",timerId,dataBaseChatId, e);
                deleteTimerAndSendNotification(
                        timerId, timer.getFullCommand(), dataBaseChatId, "Таймер с командой «%s» сломан и был удалён.", commandRoutingData
                );
            }
        };
    }
    private void deleteTimerAndSendNotification(long timerId, String command, long chatId, String messageTemplate, CommandRoutingData commandRoutingData) {
        try {
            timerService.deleteTimerById(timerId);
        } catch (Exception e) {
            log.warn("chatId {}: Failed to delete timer {} after error", chatId, timerId, e);
        }
        try {
            vkChatClient.sendText(messageMapper.toSendMessageDto(messageTemplate.formatted(command),commandRoutingData));
        } catch (Exception e) {
            log.warn("chatId {}: Failed to send notification about timer {} deletion after error", chatId, timerId, e);
        }
    }

    @Scheduled(fixedRate = MAX_SECONDS_BETWEEN_NOW_AND_EXECUTION*1_000)
    protected void putAllNearTimersToScheduledExecutor(){
        log.info("method putAllNearTimersToScheduledExecutor started");
        Instant requiredDateTime = Instant.now().plusSeconds(MAX_SECONDS_BETWEEN_NOW_AND_EXECUTION);

        Cache<Long, ScheduledFuture<?>> timerTasksCache = cacheManager.getTimerTasksCache();

        Set<Long> allWorkingTimers = timerTasksCache.asMap().keySet();
        List<TimerEntity> timersToProcess =
                timerService.getAllTimersWithNextExecutionLessThan(requiredDateTime, allWorkingTimers);

        for(TimerEntity timer: timersToProcess){
            if(timerTasksCache.getIfPresent(timer.getId())!=null){
                log.warn("method getAllTimersWithNextExecutionLessThan returned timerId {} that's already in ScheduledExecutor", timer.getId());
            }
            else{
            putTimerToScheduler(timer);
            }

        }
    }
}
