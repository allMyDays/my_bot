package com.example.my_bot.controller;

import com.example.my_bot.handler.AsyncEventHandler;
import com.example.my_bot.vk.VkMessageNew;
import com.example.my_bot.vk.VkMessageReactionEvent;
import com.example.my_bot.vk.enumeration.VkEventType;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static com.example.my_bot.vk.enumeration.VkEventType.*;


@RestController
@Slf4j
@ConditionalOnProperty(
        prefix = "vk",
        name = "mode",
        havingValue = "callback"
)
public class VkCallbackController {

    private static final Gson GSON = new Gson();

    private final String confirmationCode;
    private final AsyncEventHandler asyncEventHandler;

    public VkCallbackController(
            @Value("${vk.group.confirmation}") String confirmationCode,
            AsyncEventHandler asyncEventHandler) {

        this.confirmationCode = confirmationCode;
        this.asyncEventHandler = asyncEventHandler;
    }

    @PostMapping("/callback")
    public ResponseEntity<String> handle(@RequestBody String body){
        try{
            JsonObject update = GSON.fromJson(body, JsonObject.class);
            JsonElement typeElement = update.get("type");
            if(typeElement == null){
                return ResponseEntity.badRequest()
                        .body("Missing type field");
            }
            String type= typeElement.getAsString();

            if(MESSAGE_NEW.getValue().equals(type)){
                VkMessageNew event = GSON.fromJson(update, VkMessageNew.class);
                if(event!=null){
                    asyncEventHandler.handleNewMessageEvent(event);
                }
            }else if(MESSAGE_REACTION_EVENT.getValue().equals(type)){
                VkMessageReactionEvent event = GSON.fromJson(update, VkMessageReactionEvent.class);
                if(event!=null){
                    asyncEventHandler.handleNewReactionEvent(event);
                }
            }else if(CONFIRMATION.getValue().equals(type)){
                log.info("confirmation event came");
                return ResponseEntity.ok(confirmationCode);
            }
            return ResponseEntity.ok("ok");

        }catch (Exception e){
            log.error("Ошибка обработки callback", e);
            return ResponseEntity.internalServerError()
                    .body("error");
        }
    }
}