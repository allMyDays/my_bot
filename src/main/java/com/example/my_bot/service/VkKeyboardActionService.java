package com.example.my_bot.service;

import com.example.my_bot.dto.command.CommandRoutingData;
import com.example.my_bot.enumeration.key.ButtonPayloadKey;
import com.example.my_bot.exception.message.VkKeyboardButtonsMaxLimitException;
import com.example.my_bot.service.chat.AdminChatActionService;
import com.example.my_bot.utils.KeyboardUtils;
import com.example.my_bot.vk.mapping.button.VkButtonConfig;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.objects.messages.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class VkKeyboardActionService {

    private final static int MAX_CREATABLE_BUTTONS = 40;
    private final static int MAX_BUTTONS_PER_ONE_ROW = 5;
    private final static int MIN_BUTTONS_PER_ONE_ROW = 1;

    private final AdminChatActionService adminChatActionService;

    public VkKeyboardActionService(@Lazy AdminChatActionService adminChatActionService) {
        this.adminChatActionService = adminChatActionService;
    }


    public Keyboard createAutoLayoutKeyboard(@NonNull List<VkButtonConfig> buttons, int buttonsPerRow){
        // Проверка лимитов
        if(buttons.size()>MAX_CREATABLE_BUTTONS){
            throw new VkKeyboardButtonsMaxLimitException(MAX_CREATABLE_BUTTONS);
        }
        if(buttonsPerRow<MIN_BUTTONS_PER_ONE_ROW||buttonsPerRow>MAX_BUTTONS_PER_ONE_ROW){
            throw new VkKeyboardButtonsMaxLimitException(
                    "В одной строке может быть от %d до %d кнопок".formatted(MIN_BUTTONS_PER_ONE_ROW, MAX_BUTTONS_PER_ONE_ROW)
            );
        }

        List<List<KeyboardButton>> allRows = new ArrayList<>();
        List<KeyboardButton> currentRow = new ArrayList<>();

        for(VkButtonConfig config: buttons){

            KeyboardButtonActionText actionText = new KeyboardButtonActionText();
            actionText.setLabel(config.getText());
            actionText.setType(KeyboardButtonActionTextType.TEXT);
            actionText.setPayload(config.getPayload());

            KeyboardButton button = new KeyboardButton()
                    .setAction(actionText)
                    .setColor(config.getColor());

            currentRow.add(button);

            if(currentRow.size()==buttonsPerRow){
                allRows.add(currentRow);
                currentRow = new ArrayList<>();
            }
        }
        // Добавляю последнюю неполную строку если она есть
        if (!currentRow.isEmpty()) {
            allRows.add(currentRow);
        }

        return new Keyboard()
                .setButtons(allRows)
                .setOneTime(true);
    }

    public Keyboard createAutoLayoutKeyboard(@NonNull List<VkButtonConfig> buttons){
        return createAutoLayoutKeyboard(buttons, MAX_BUTTONS_PER_ONE_ROW);
    }



    public void handleClickedButton(@NonNull String messageText, @NonNull String buttonPayload, @NonNull CommandRoutingData routingData, long fromId) throws ClientException, ApiException {

        long theBotId = routingData.getReceivedEventBot().getGroupId();

        if(!KeyboardUtils.isClickedButtonBelongsToRequiredBot(messageText, theBotId)) return;

        Optional<Map.Entry<ButtonPayloadKey, String>> payloadData = KeyboardUtils.extractKeyAndValueFromPayload(buttonPayload);

        if(payloadData.isEmpty()){
            log.warn("came invalid button payload {}", buttonPayload);
            return;
        }

        switch (payloadData.get().getKey()){
            case ADMIN_CHAT_EXECUTE_COMMAND_IN_ONE_BOUND_CHAT,
                  ADMIN_CHAT_EXECUTE_COMMAND_IN_ALL_BOUND_CHATS,
                   ADMIN_CHAT_EXECUTE_COMMAND_IN_THIS_ADMIN_CHAT -> {
                 adminChatActionService.handleClickedAdminChatButton(routingData, payloadData.get().getKey(), payloadData.get().getValue(), fromId);
            }
        }


    }







}
