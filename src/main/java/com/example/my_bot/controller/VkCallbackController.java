package com.example.my_bot.controller;

import com.example.my_bot.handler.AsyncEventHandler;
import com.example.my_bot.vk.VkMessageNew;
import com.example.my_bot.vk.enumeration.VkEventType;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static com.example.my_bot.vk.enumeration.VkEventType.CONFIRMATION;
import static com.example.my_bot.vk.enumeration.VkEventType.MESSAGE_NEW;


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

        try {
            VkMessageNew event = GSON.fromJson(body, VkMessageNew.class);

            if(event==null||event.getType()==null){
                return ResponseEntity.badRequest().body("invalid event");
            }

            VkEventType type = event.getType();

            if(CONFIRMATION.equals(type)){
                log.info("confirmation event came");
                return ResponseEntity.ok(confirmationCode);

            }
            if(MESSAGE_NEW.equals(type)){
                asyncEventHandler.handleMessageNew(event);
            }
            return ResponseEntity.ok("ok");

        }catch (Exception e){
            log.error("Ошибка обработки callback", e);
            return ResponseEntity.internalServerError()
                    .body("error");
        }
    }
}