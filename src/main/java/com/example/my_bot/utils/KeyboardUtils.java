package com.example.my_bot.utils;


import com.example.my_bot.enumeration.key.ButtonPayloadKey;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.AbstractMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
public class KeyboardUtils {

    public static String createButtonPayload(@NonNull ButtonPayloadKey buttonPayloadKey, @NonNull String buttonPayloadValue) {
        JsonObject payload = new JsonObject();
        payload.addProperty(buttonPayloadKey.getStringValue(), buttonPayloadValue);
        return payload.toString();
    }

    public static Optional<Map.Entry<ButtonPayloadKey, String>> extractKeyAndValueFromPayload(@NonNull String payloadString){
        try {
            JsonObject payload = JsonParser.parseString(payloadString).getAsJsonObject();

            Set<String> jsonKeys = payload.keySet();
            for (String jsonKey : jsonKeys) {
                Optional<ButtonPayloadKey> keyOpt = ButtonPayloadKey.findKeyByStringValue(jsonKey);
                if (keyOpt.isPresent()) {
                    String value = payload.get(jsonKey).getAsString();
                    return Optional.of(new AbstractMap.SimpleEntry<>(keyOpt.get(), value));
                }
            }
        }catch (Exception e) {
            log.warn("error extracting key and value from button payload: {}", payloadString);
        }
        return Optional.empty();
    }



    public static boolean isClickedButtonBelongsToRequiredBot(@NonNull String messageText, long botId){
        return messageText.trim().startsWith("[club%d|".formatted(Math.abs(botId)));
    }
}
