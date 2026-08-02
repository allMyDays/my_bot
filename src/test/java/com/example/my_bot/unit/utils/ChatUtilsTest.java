package com.example.my_bot.unit.utils;

import com.example.my_bot.utils.ChatUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class ChatUtilsTest {

    private static final String ALLOWED_CHARS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    @Test
    @DisplayName("generateNewChatCode() returns string of length 10")
    void generateNewChatCode_shouldReturnLength10() {
        String code = ChatUtils.generateNewChatCode();
        assertEquals(10, code.length());
    }

    @Test
    @DisplayName("generateNewChatCode() returns only allowed characters")
    void generateNewChatCode_shouldContainOnlyAllowedChars() {
        String code = ChatUtils.generateNewChatCode();
        for (char c : code.toCharArray()) {
            assertTrue(ALLOWED_CHARS.indexOf(c) >= 0,
                    "Character '" + c + "' is not allowed in chat code");
        }
    }

    @Test
    @DisplayName("generateNewChatCode() generates different codes (probabilistic)")
    void generateNewChatCode_shouldGenerateDifferentCodes() {
        String code1 = ChatUtils.generateNewChatCode();
        String code2 = ChatUtils.generateNewChatCode();
        assertNotEquals(code1, code2);
    }


    @ParameterizedTest
    @DisplayName("extractConversationId() returns correct id for chat peer ids")
    @CsvSource({
            "2000000000, 0",
            "2000000005, 5",
            "3000000000, 1000000000"
    })
    void extractConversationId_withChatPeer_shouldReturnChatId(long peerId, long expectedChatId) {
        assertEquals(expectedChatId, ChatUtils.extractConversationId(peerId));
    }

    @ParameterizedTest
    @DisplayName("extractConversationId() throws exception for personal peer ids")
    @ValueSource(longs = { -1, 0, 1999999999, Long.MIN_VALUE })
    void extractConversationId_withPersonalPeer_shouldThrowIllegalArgumentException(long peerId) {
        assertThrows(IllegalArgumentException.class,
                () -> ChatUtils.extractConversationId(peerId));
    }


    @ParameterizedTest
    @DisplayName("isPersonalChat() returns true for personal peer ids")
    @ValueSource(longs = { -1, 0, 1999999999, Long.MIN_VALUE })
    void isPersonalChat_withPersonalPeer_shouldReturnTrue(long peerId) {
        assertTrue(ChatUtils.isPersonalChat(peerId));
    }

    @ParameterizedTest
    @DisplayName("isPersonalChat() returns false for chat peer ids")
    @ValueSource(longs = { 2000000000, 2000000001, Long.MAX_VALUE })
    void isPersonalChat_withChatPeer_shouldReturnFalse(long peerId) {
        assertFalse(ChatUtils.isPersonalChat(peerId));
    }

    @ParameterizedTest
    @DisplayName("convertToPeerId() returns correct peer id for valid chat ids")
    @CsvSource({
            "0, 2000000000",
            "1, 2000000001",
            "1000000000, 3000000000"
    })
    void convertToPeerId_withValidChatId_shouldReturnPeerId(long chatId, long expectedPeerId) {
        assertEquals(expectedPeerId, ChatUtils.convertToPeerId(chatId));
    }

    @ParameterizedTest
    @DisplayName("convertToPeerId() throws exception for invalid chat ids")
    @ValueSource(longs = { -1, -100, 1000000001, Long.MAX_VALUE })
    void convertToPeerId_withInvalidChatId_shouldThrowIllegalArgumentException(long chatId) {
        assertThrows(IllegalArgumentException.class,
                () -> ChatUtils.convertToPeerId(chatId));
    }

    @ParameterizedTest
    @DisplayName("isGroupId() returns true for negative member ids")
    @ValueSource(longs = { -1, -100, Long.MIN_VALUE })
    void isGroupId_withNegativeMemberId_shouldReturnTrue(long memberId) {
        assertTrue(ChatUtils.isGroupId(memberId));
    }

    @ParameterizedTest
    @DisplayName("isGroupId() returns false for non-negative member ids")
    @ValueSource(longs = { 0, 1, 100, Long.MAX_VALUE })
    void isGroupId_withNonNegativeMemberId_shouldReturnFalse(long memberId) {
        assertFalse(ChatUtils.isGroupId(memberId));
    }

    @ParameterizedTest
    @DisplayName("buildGroupWallPostAsAttachment() returns correct attachment string")
    @CsvSource({
            "123, 456, wall-123_456",
            "-123, 456, wall-123_456",
            "0, 789, wall0_789",
            "1, 0, wall-1_0"
    })
    void buildGroupWallPostAsAttachment_shouldReturnExpectedString(long groupId, int postId, String expected) {
        assertEquals(expected, ChatUtils.buildGroupWallPostAsAttachment(groupId, postId));
    }
}