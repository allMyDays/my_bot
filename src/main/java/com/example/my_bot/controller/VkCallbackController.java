package com.example.my_bot.controller;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.CommandDispatcher;
import com.example.my_bot.mapper.CommandMapper;
import com.example.my_bot.service.ChatActionService;
import com.example.my_bot.service.MemberService;
import com.example.my_bot.service.UserService;
import com.google.gson.Gson;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.objects.callback.MessageNew;
import com.vk.api.sdk.objects.callback.Type;
import com.vk.api.sdk.objects.messages.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

import static com.example.my_bot.constant.MessageConstant.UNKNOWN_ERROR_MESSAGE;
import static com.example.my_bot.utils.ChatUtils.extractConversationId;
import static com.example.my_bot.utils.ChatUtils.isPersonalChat;
import static com.vk.api.sdk.objects.callback.Type.CONFIRMATION;
import static com.vk.api.sdk.objects.callback.Type.MESSAGE_NEW;

@RestController
@Slf4j
public class VkCallbackController {

    private final String confirmationCode;

    private final CommandDispatcher commandDispatcher;

    private final VkChatClient vkChatClient;

    private final CommandMapper commandMapper;

    private final UserService userService;

    private final ChatActionService chatActionService;

    public VkCallbackController(CommandMapper commandMapper,
                                VkChatClient vkChatClient,
                                CommandDispatcher commandDispatcher,
                                UserService userService,
                                ChatActionService chatActionService,
                                @Value("${vk.group.confirmation}")String confirmationCode) {
        this.commandMapper = commandMapper;
        this.vkChatClient = vkChatClient;
        this.commandDispatcher = commandDispatcher;
        this.confirmationCode = confirmationCode;
        this.userService = userService;
        this.chatActionService = chatActionService;
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
            long peerId = message.getPeerId();
            long fromId = message.getFromId();
            long chatId;

            if(isPersonalChat(peerId)){
                Optional<Long> boundChat = userService.getOrCreateUser(fromId).getOptionalBoundChat();
                if(boundChat.isEmpty()){
                    return "ok";
                }else{
                    chatId=boundChat.get();
                }
            }else{
                chatId = extractConversationId(peerId);
            }

            try {
                 commandDispatcher.dispatch(commandMapper.toCommandMessageDto(chatId, message));
                 chatActionService.checkLastChatSynchronizationAndExecute(chatId);

                 if(!isPersonalChat(peerId)){
                     chatActionService.handleChatAction(chatId,fromId,message.getAction());
                 }



            }catch (Exception e) {
                log.error("Произошла ошибка: ",e);
                try {
                    vkChatClient.sendText(UNKNOWN_ERROR_MESSAGE, peerId,true);
                } catch (ClientException|ApiException e2) {
                    log.error("Ошибка при попытке отправить сообщение об ошибке в диалог c peerId {}: ",peerId,e2);

                  }
                }

        }
        return "ok";
    }
}