package com.example.my_bot.longpoll;

import com.example.my_bot.handler.AsyncEventHandler;
import com.example.my_bot.vk.mapping.message.VkMessageNew;
import com.example.my_bot.vk.mapping.reaction.VkMessageReactionEvent;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.objects.callback.longpoll.responses.GetLongPollEventsResponse;
import com.vk.api.sdk.objects.groups.LongPollServer;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import static com.example.my_bot.vk.enumeration.VkEventType.MESSAGE_NEW;
import static com.example.my_bot.vk.enumeration.VkEventType.MESSAGE_REACTION_EVENT;

@Service
@Slf4j
public class VkLongPollBot {

    private static final Gson GSON = new Gson();
    private static final int WAIT_TIME_SECONDS = 25;
    private static final long RETRY_DELAY_MS = 5000;

    private final long groupId;
    private final AsyncEventHandler asyncEventHandler;
    private final VkApiClient vkApiClient;
    private final GroupActor groupActor;

    private volatile boolean running = true;
    private Thread pollingThread;

    public VkLongPollBot(AsyncEventHandler asyncEventHandler, VkApiClient vkApiClient, GroupActor groupActor){
        this.asyncEventHandler = asyncEventHandler;
        this.vkApiClient = vkApiClient;
        this.groupActor = groupActor;
        this.groupId = groupActor.getGroupId();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startPolling() {

        pollingThread = new Thread(this::pollingLoop);
        pollingThread.setName("vk-long-poll-thread");
        pollingThread.start();
        log.info("VK LongPoll бот запущен.");
    }

    @PreDestroy
    public void stopPolling() {
        running = false;
        if (pollingThread != null) {
            pollingThread.interrupt();
        }
        log.info("VK LongPoll бот остановлен.");
    }

    private void pollingLoop() {
        LongPollServerData serverData = refreshLongPollServer(groupActor);

        if (serverData == null) {
            log.error("Не удалось получить параметры LongPoll сервера.");
            return;
        }

        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                GetLongPollEventsResponse response = vkApiClient.longPoll()
                        .getEvents(serverData.server(), serverData.key(), serverData.ts())
                        .waitTime(WAIT_TIME_SECONDS)
                        .execute();

                serverData = new LongPollServerData(
                        serverData.server(),
                        serverData.key(),
                        response.getTs()
                );
                if(response.getUpdates()== null){
                    continue;
                }
                for(JsonObject update:response.getUpdates()){
                    JsonElement typeElement = update.get("type");
                    if(typeElement == null) continue;
                    String type= typeElement.getAsString();

                    if (MESSAGE_NEW.getValue().equals(type)){
                        VkMessageNew event = GSON.fromJson(update, VkMessageNew.class);
                        asyncEventHandler.handleNewMessageEvent(event, false);
                    }
                    else if (MESSAGE_REACTION_EVENT.getValue().equals(type)){
                        VkMessageReactionEvent event = GSON.fromJson(update, VkMessageReactionEvent.class);
                        asyncEventHandler.handleNewReactionEvent(event, false);
                    }

                }

            }catch (ApiException e){
                log.warn("Ошибка VK API. Код: {}, сообщение: {}", e.getCode(), e.getMessage());
                serverData = refreshLongPollServer(groupActor);

                if(serverData==null){
                    sleepBeforeRetry();
                }
            } catch (ClientException e){
                log.warn("Ошибка соединения с VK LongPoll: {}", e.getMessage());
                sleepBeforeRetry();

            }catch (Exception e){
                log.error("Непредвиденная ошибка в polling loop", e);
                sleepBeforeRetry();
            }
        }
    }

    private LongPollServerData refreshLongPollServer(GroupActor groupActor) {

        try {
            LongPollServer longPollServer = vkApiClient.groups()
                    .getLongPollServer(groupActor, groupId)
                    .execute();
            log.info("Получены параметры LongPoll сервера. ts={}", longPollServer.getTs());

            return new LongPollServerData(
                    longPollServer.getServer(),
                    longPollServer.getKey(),
                    longPollServer.getTs()
            );

        } catch (ApiException|ClientException e){
            log.error("Не удалось получить параметры LongPoll сервера: {}", e.getMessage());
            return null;
        }
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(RETRY_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.info("Polling thread interrupted.");
        }
    }

    private record LongPollServerData(java.net.URI server, String key, String ts){}
}