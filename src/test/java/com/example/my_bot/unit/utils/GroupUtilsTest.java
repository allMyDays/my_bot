package com.example.my_bot.unit.utils;

import com.example.my_bot.utils.GroupUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class GroupUtilsTest {

    private static final String ALLOWED_SECRET_CHARS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_";

    @ParameterizedTest
    @DisplayName("isGroupToken() returns true for valid tokens")
    @CsvSource({
            "vk1.a.12345678901234567890123456789012345678901234567890, true",
            "vk1.z.abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_-, true",
            "  vk1.b.ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz1234567890  , true"
    })
    void isGroupToken_withValidToken_shouldReturnTrue(String token, boolean expected) {
        assertEquals(expected, GroupUtils.isGroupToken(token));
    }

    @ParameterizedTest
    @DisplayName("isGroupToken() returns false for invalid tokens")
    @CsvSource({
            "vk0.a.12345678901234567890123456789012345678901234567890, false",
            "vk1.A.12345678901234567890123456789012345678901234567890, false",
            "vk1.a.1234567890123456789012345678901234567890123456789, false",
            "vk1.a.12345678901234567890123456789012345678901234567890., false",
            ", false",
            "   , false"
    })
    void isGroupToken_withInvalidToken_shouldReturnFalse(String token, boolean expected) {
        if (token == null || token.trim().isEmpty()) {
            assertFalse(GroupUtils.isGroupToken(token == null ? "" : token));
        } else {
            assertEquals(expected, GroupUtils.isGroupToken(token));
        }
    }

    @Test
    @DisplayName("isGroupToken() throws NPE when token is null (if @NonNull enforced)")
    void isGroupToken_withNull_shouldThrowNullPointerException() {
        assertThrows(NullPointerException.class, () -> GroupUtils.isGroupToken(null));
    }


    @ParameterizedTest
    @DisplayName("createPrivateMessagesLink() returns correct link")
    @CsvSource({
            "123, vk.me/club123",
            "-123, vk.me/club123",
            "0, vk.me/club0",
            "1, vk.me/club1",
            "-1, vk.me/club1"
    })
    void createPrivateMessagesLink_shouldReturnExpectedLink(long groupId, String expected) {
        assertEquals(expected, GroupUtils.createPrivateMessagesLink(groupId));
    }

    @Test
    @DisplayName("createPrivateMessagesLink() handles Long.MIN_VALUE (Math.abs bug)")
    void createPrivateMessagesLink_withMinValue_shouldReturnClubMinValue() {
        long minValue = Long.MIN_VALUE;
        String expected = "vk.me/club" + Long.MIN_VALUE;
        assertEquals(expected, GroupUtils.createPrivateMessagesLink(minValue));
    }


    @Test
    @DisplayName("generateCBServerSecretKey() returns key of length 50")
    void generateCBServerSecretKey_shouldReturnLength50() {
        String key = GroupUtils.generateCBServerSecretKey();
        assertEquals(50, key.length());
    }

    @Test
    @DisplayName("generateCBServerSecretKey() returns only allowed characters")
    void generateCBServerSecretKey_shouldContainOnlyAllowedChars() {
        String key = GroupUtils.generateCBServerSecretKey();
        for (char c : key.toCharArray()) {
            assertTrue(ALLOWED_SECRET_CHARS.indexOf(c) >= 0,
                    "Character '" + c + "' is not allowed in secret key");
        }
    }

    @Test
    @DisplayName("generateCBServerSecretKey() generates different keys (probabilistic)")
    void generateCBServerSecretKey_shouldGenerateDifferentKeys() {
        String key1 = GroupUtils.generateCBServerSecretKey();
        String key2 = GroupUtils.generateCBServerSecretKey();
        assertNotEquals(key1, key2);
    }
}