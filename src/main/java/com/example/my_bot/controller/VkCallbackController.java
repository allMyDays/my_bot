package com.example.my_bot.controller;

import com.example.my_bot.cache.value.callback.SecretKeyAndConfirmationCodeAndCompletableFuture;
import com.example.my_bot.config.CaffeineCacheManager;
import com.example.my_bot.handler.AsyncEventHandler;
import com.example.my_bot.vk.mapping.BaseVkEventInfo;
import com.example.my_bot.vk.mapping.message.VkMessageNew;
import com.example.my_bot.vk.mapping.post.VkWallPostNew;
import com.example.my_bot.vk.mapping.reaction.VkMessageReactionEvent;
import com.google.gson.Gson;
import com.vk.api.sdk.objects.callback.Base;
import com.vk.api.sdk.objects.callback.MessageNew;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;


@RestController
@Slf4j
public class VkCallbackController {

    private static final Gson GSON = new Gson();
    private final AsyncEventHandler asyncEventHandler;
    private final CaffeineCacheManager cacheManager;

    private final static String OK_STRING_RESPONSE = "ok";
    private final String submanagerApiVersion;

    public VkCallbackController(AsyncEventHandler asyncEventHandler, CaffeineCacheManager cacheManager, @Value("${vk.submanager.callback.server.api-version}") String submanagerApiVersion){
        this.asyncEventHandler = asyncEventHandler;
        this.cacheManager = cacheManager;
        this.submanagerApiVersion = submanagerApiVersion;
        MessageNew M;
    }

    @PostMapping("/callback")
    public ResponseEntity<String> handle(@RequestBody String body){
        try{
            BaseVkEventInfo base = GSON.fromJson(body, BaseVkEventInfo.class);

            if(base.getGroupId()==null||base.getType()==null||base.getSecret()==null||base.getV()==null){
                log.info("came callback event without important fields: {}", body);
                return ResponseEntity.ok(OK_STRING_RESPONSE);
            }
            if(!submanagerApiVersion.equals(base.getV())){
                log.info("came callback event with api version that's not for submanagers: {}", body);
                return ResponseEntity.ok(OK_STRING_RESPONSE);
            }

            switch (base.getType()){
                case MESSAGE_NEW -> {
                    VkMessageNew event= GSON.fromJson(body, VkMessageNew.class);
                    asyncEventHandler.handleNewMessageEvent(event, true);
                }
                case MESSAGE_REACTION_EVENT-> {
                    VkMessageReactionEvent event = GSON.fromJson(body, VkMessageReactionEvent.class);
                    asyncEventHandler.handleNewReactionEvent(event, true);
                }
                case WALL_POST_NEW -> {
                    VkWallPostNew event = GSON.fromJson(body, VkWallPostNew.class);
                    asyncEventHandler.handleNewWallPostEvent(event);
                }
                case CONFIRMATION -> {
                    log.info("callback confirmation event came: {}", body);
                    SecretKeyAndConfirmationCodeAndCompletableFuture confirmation =
                            cacheManager.getConfirmationCallbackUrlCache().getIfPresent(base.getGroupId());
                    if(confirmation==null){
                        log.warn("came callback confirmation event, but there's no data in cache to confirm: {}", body);
                        return ResponseEntity.notFound()
                                .build();
                    }
                    if(!confirmation.getSecretKey().equals(base.getSecret())){
                        log.warn("came callback confirmation event, but secret keys don't match: {},{}", confirmation.getSecretKey(),body);
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                .build();
                    }
                    CompletableFuture<Boolean> futureResult= confirmation.getFutureConfirmationResult();
                    if(!futureResult.isDone()){
                        futureResult.complete(true);
                    }
                    return ResponseEntity.ok(confirmation.getConfirmationCode());
                }
            }

            return ResponseEntity.ok(OK_STRING_RESPONSE);

        }catch (Exception e){
            log.error("Ошибка обработки callback запроса", e);
            return ResponseEntity.ok(OK_STRING_RESPONSE);
        }
    }
}