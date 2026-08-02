package com.example.my_bot.unit.utils;

import com.example.my_bot.enumeration.key.ButtonPayloadKey;
import com.example.my_bot.utils.KeyboardUtils;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.AbstractMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class KeyboardUtilsTest {

    @Test
    void createButtonPayload_shouldReturnValidJson() {
        ButtonPayloadKey key = ButtonPayloadKey.ADMIN_CHAT_EXECUTE_COMMAND_IN_ONE_BOUND_CHAT;
        String value = "test_value";
        String payload = KeyboardUtils.createButtonPayload(key, value);
        JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
        assertTrue(json.has(key.getStringValue()));
        assertEquals(value, json.get(key.getStringValue()).getAsString());
    }

    @Test
    void extractKeyAndValueFromPayload_withValidPayload_shouldReturnEntry() {
        ButtonPayloadKey key = ButtonPayloadKey.ADMIN_CHAT_EXECUTE_COMMAND_IN_ALL_BOUND_CHATS;
        String value = "start";
        String payload = KeyboardUtils.createButtonPayload(key, value);
        Optional<Map.Entry<ButtonPayloadKey, String>> result = KeyboardUtils.extractKeyAndValueFromPayload(payload);
        assertTrue(result.isPresent());
        assertEquals(key, result.get().getKey());
        assertEquals(value, result.get().getValue());
    }

    @Test
    void extractKeyAndValueFromPayload_withEmptyPayload_shouldReturnEmpty() {
        String payload = "{}";
        Optional<Map.Entry<ButtonPayloadKey, String>> result = KeyboardUtils.extractKeyAndValueFromPayload(payload);
        assertFalse(result.isPresent());
    }

    @Test
    void extractKeyAndValueFromPayload_withInvalidJson_shouldReturnEmpty() {
        String payload = "invalid json";
        Optional<Map.Entry<ButtonPayloadKey, String>> result = KeyboardUtils.extractKeyAndValueFromPayload(payload);
        assertFalse(result.isPresent());
    }

    @Test
    void extractKeyAndValueFromPayload_withKeyNotRecognized_shouldReturnEmpty() {
        JsonObject json = new JsonObject();
        json.addProperty("unknown_key", "some_value");
        String payload = json.toString();
        Optional<Map.Entry<ButtonPayloadKey, String>> result = KeyboardUtils.extractKeyAndValueFromPayload(payload);
        assertFalse(result.isPresent());
    }

    @ParameterizedTest
    @CsvSource({
            "[club123|some text, 123, true",
            "[club123|, 123, true",
            "[club456|text, 123, false",
            " [club123|text, 123, true",
            "[club123|text, 456, false",
            "club123|text, 123, false",
            "[club-123|text, 123, false"
    })
    void isClickedButtonBelongsToRequiredBot_shouldReturnExpected(String messageText, long botId, boolean expected) {
        assertEquals(expected, KeyboardUtils.isClickedButtonBelongsToRequiredBot(messageText, botId));
    }

    @Test
    void isClickedButtonBelongsToRequiredBot_withNullMessage_throwsNPE() {
        assertThrows(NullPointerException.class, () -> KeyboardUtils.isClickedButtonBelongsToRequiredBot(null, 123));
    }
}