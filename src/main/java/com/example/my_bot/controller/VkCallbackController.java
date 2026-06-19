package com.example.my_bot.controller;

import com.example.my_bot.cache.value.callback.SecretKeyAndConfirmationCodeAndCompletableFuture;
import com.example.my_bot.config.CaffeineCacheManager;
import com.example.my_bot.handler.AsyncEventHandler;
import com.example.my_bot.vk.VkCallbackEventBaseInfo;
import com.example.my_bot.vk.VkMessageNew;
import com.example.my_bot.vk.VkMessageReactionEvent;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;

import static com.example.my_bot.vk.enumeration.VkEventType.*;


@RestController
@Slf4j
public class VkCallbackController {

    private static final Gson GSON = new Gson();
    private final AsyncEventHandler asyncEventHandler;
    private final CaffeineCacheManager cacheManager;

    private final static String OK_STRING_RESPONSE = "ok";

    public VkCallbackController(AsyncEventHandler asyncEventHandler, CaffeineCacheManager cacheManager){
        this.asyncEventHandler = asyncEventHandler;
        this.cacheManager = cacheManager;
    }

    @PostMapping("/callback")
    public ResponseEntity<String> handle(@RequestBody String body){
        try{
            VkCallbackEventBaseInfo baseInfo = GSON.fromJson(body, VkCallbackEventBaseInfo.class);

            if(baseInfo==null||baseInfo.getType()==null||baseInfo.getGroupId()==null||baseInfo.getSecretKey()==null){
                log.info("came callback event without important fields: {}", body);
                return ResponseEntity.ok(OK_STRING_RESPONSE);
            }

            switch (baseInfo.getType()){
                case MESSAGE_NEW -> {
                    VkMessageNew event= GSON.fromJson(body, VkMessageNew.class);
                    if(event!=null){
                        asyncEventHandler.handleNewMessageEvent(event, true);
                    }
                }
                case MESSAGE_REACTION_EVENT-> {
                    VkMessageReactionEvent event = GSON.fromJson(body, VkMessageReactionEvent.class);
                    if(event!=null){
                        asyncEventHandler.handleNewReactionEvent(event, true);
                    }
                }
                case CONFIRMATION -> {
                    log.info("callback confirmation event came: {}", body);
                    SecretKeyAndConfirmationCodeAndCompletableFuture confirmation =
                            cacheManager.getConfirmationCallbackUrlCache().getIfPresent(baseInfo.getGroupId());
                    if(confirmation==null){
                        log.warn("came callback confirmation event, but there's no data in cache to confirm: {}", body);
                        return ResponseEntity.notFound()
                                .build();
                    }
                    if(!confirmation.getSecretKey().equals(baseInfo.getSecretKey())){
                        log.warn("came callback confirmation event, but secret keys don't match: {},{}", confirmation.getSecretKey(),body);
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                .build();
                    }
                    CompletableFuture<Boolean> futureResult=  confirmation.getFutureConfirmationResult();
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