package com.example.my_bot.controller;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.CommandDispatcher;
import com.example.my_bot.service.MemberService;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.objects.callback.MessageNew;
import com.vk.api.sdk.objects.callback.Type;
import com.vk.api.sdk.objects.messages.ActionOneOf;
import com.vk.api.sdk.objects.messages.Message;
import com.vk.api.sdk.objects.messages.MessageActionStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static com.example.my_bot.constant.MessageConstant.UNKNOWN_ERROR_MESSAGE;
import static com.example.my_bot.constant.MessageConstant.WELCOME_MESSAGE;
import static com.example.my_bot.utils.VkChatUtils.extractConversationId;
import static com.example.my_bot.utils.VkChatUtils.isPersonalChat;
import static com.vk.api.sdk.objects.callback.Type.CONFIRMATION;
import static com.vk.api.sdk.objects.callback.Type.MESSAGE_NEW;
import static com.vk.api.sdk.objects.messages.MessageActionStatus.CHAT_INVITE_USER;

@RestController
@Slf4j
public class VkCallbackController {

    private final String confirmationCode;

    private final CommandDispatcher commandDispatcher;

    private final long groupId;

    private final VkChatClient vkChatClient;

    private final MemberService memberService;

    public VkCallbackController(
            @Value("${vk.group.confirmation}") String confirmationCode,
            @Value("${vk.group.id}") long groupId,
            CommandDispatcher commandDispatcher, VkChatClient vkChatClient, MemberService memberService) {
        this.confirmationCode = confirmationCode;
        this.commandDispatcher=commandDispatcher;
        this.groupId = groupId;
        this.vkChatClient = vkChatClient;
        this.memberService = memberService;
    }

    @PostMapping("/callback")
    public String handle(@RequestBody String body) {

        MessageNew event = new Gson().fromJson(body, MessageNew.class);

        Type type = event.getType();
        if (CONFIRMATION.equals(type)) {
            return confirmationCode;
        }

        if (MESSAGE_NEW.equals(type)) {
            Message message = event.getObject().getMessage();
            String text = message.getText();
            long peerId = message.getPeerId();
            long fromId = message.getFromId();

            if(!isPersonalChat(peerId)){
                long chatId = extractConversationId(peerId);
                try {

                ActionOneOf action = message.getAction();
                if (action != null) {
                    MessageActionStatus actionType = action.getType();
                    Long memberId = action.getMemberId();
                    if(memberId!=null){
                    if (CHAT_INVITE_USER.equals(actionType) && memberId== -groupId) {
                        vkChatClient.sendText(chatId, WELCOME_MESSAGE,true);
                        return "ok";
                     }
                    }
                  }


                 commandDispatcher.dispatch(text, chatId, fromId);
                 memberService.checkLastSyncAndPerform(chatId);

            }catch (Exception e) {
                log.error("Произошла ошибка: ",e);

                try {
                    vkChatClient.sendText(extractConversationId(peerId),UNKNOWN_ERROR_MESSAGE,true);
                } catch (ClientException|ApiException e2) {
                    log.error("Ошибка при попытке отправить сообщение об ошибке в чат: ",e2);

                  }
                }
            }
        }
        return "ok";
    }
}