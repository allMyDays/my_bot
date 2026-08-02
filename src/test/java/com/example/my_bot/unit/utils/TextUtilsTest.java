package com.example.my_bot.unit.utils;

import com.example.my_bot.utils.TextUtils;
import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.lang.UProperty;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.text.BreakIterator;

import static org.junit.jupiter.api.Assertions.*;

class TextUtilsTest {

    @ParameterizedTest
    @CsvSource({
            "123, @id123",
            "-123, @club123",
            "0, @id0",
            "-0, @id0"
    })
    void createMention_shouldReturnExpected(long memberId, String expected) {
        assertEquals(expected, TextUtils.createMention(memberId));
    }

    @ParameterizedTest
    @CsvSource({
            "123, vk.com/id123",
            "-123, vk.com/club123",
            "0, vk.com/id0",
            "-0, vk.com/id0"
    })
    void createMemberLink_shouldReturnExpected(long memberId, String expected) {
        assertEquals(expected, TextUtils.createMemberLink(memberId));
    }

    @ParameterizedTest
    @CsvSource({
            "123, id123",
            "-123, club123",
            "0, id0",
            "-0, id0"
    })
    void createMentionBody_shouldReturnExpected(long memberId, String expected) {
        assertEquals(expected, TextUtils.createMentionBody(memberId));
    }

    @ParameterizedTest
    @CsvSource({
            "123, true",
            "-123, true",
            "0, true",
            "2147483647, true",
            "-2147483648, true",
            "2147483648, false",
            "-2147483649, false",
            "12a, false",
            "  123  , true",
            "  -456  , true",
            "abc, false"
    })
    void isValidInteger_shouldReturnExpected(String str, boolean expected) {
        assertEquals(expected, TextUtils.isValidInteger(str));
    }

    @Test
    void isValidInteger_withNull_shouldReturnFalse() {
        assertFalse(TextUtils.isValidInteger(null));
    }

    @ParameterizedTest
    @CsvSource({
            "123, true",
            "-123, true",
            "0, true",
            "9223372036854775807, true",
            "-9223372036854775808, true",
            "9223372036854775808, false",
            "-9223372036854775809, false",
            "12a, false",
            "  123  , true",
            "abc, false"
    })
    void isValidLong_shouldReturnExpected(String str, boolean expected) {
        assertEquals(expected, TextUtils.isValidLong(str));
    }

    @Test
    void isValidLong_withNull_shouldReturnFalse() {
        assertFalse(TextUtils.isValidLong(null));
    }

    @ParameterizedTest
    @CsvSource({
            "123, true",
            "-123, true",
            "0, true",
            "  456  , true",
            "12a, false",
            "abc, false"
    })
    void isNumber_shouldReturnExpected(String str, boolean expected) {
        assertEquals(expected, TextUtils.isNumber(str));
    }

    @Test
    void isNumber_withNull_throwsNPE() {
        assertThrows(NullPointerException.class, () -> TextUtils.isNumber(null));
    }

    @Test
    void collectArgumentsSinceIndex_shouldReturnJoinedArguments() {
        String[] args = {"a", "b", "c", "d"};
        assertEquals("c d", TextUtils.collectArgumentsSinceIndex(args, 2));
        assertEquals("a b c d", TextUtils.collectArgumentsSinceIndex(args, 0));
        assertEquals("d", TextUtils.collectArgumentsSinceIndex(args, 3));
    }

    @Test
    void collectArgumentsSinceIndex_withIndexOutOfBounds_throws() {
        String[] args = {"a", "b", "c", "d"};
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> TextUtils.collectArgumentsSinceIndex(args, 4));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> TextUtils.collectArgumentsSinceIndex(args, -1));
    }

    @ParameterizedTest
    @CsvSource({
            "!hello, hello",
            "hello, hello",
            "  !hello  , hello",
            "  hello  , hello",
            "!, ''"
    })
    void cutDefaultPrefix_shouldReturnExpected(String input, String expected) {
        assertEquals(expected, TextUtils.cutDefaultPrefix(input));
    }

    @Test
    void cutDefaultPrefix_withNull_returnsNull() {
        assertNull(TextUtils.cutDefaultPrefix(null));
    }

    @ParameterizedTest
    @CsvSource({
            "HELLO, true",
            "Hello, false",
            "hELLO, false",
            "HELLO WORLD, true",
            "HeLlO, false",
            "A, false",
            "AB, false",
            "ABC, false",
            "ABCD, true",
            "ABCDef, false",
            "ABCDEf, true",
            "   HELLO   , true",
            "   Hello   , false"
    })
    void isMostlyCaps_shouldReturnExpected(String text, boolean expected) {
        assertEquals(expected, TextUtils.isMostlyCaps(text));
    }

    @Test
    void isMostlyCaps_withNull_throwsNPE() {
        assertThrows(NullPointerException.class, () -> TextUtils.isMostlyCaps(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void isMostlyCaps_withEmptyOrBlank_shouldReturnFalse(String text) {
        assertFalse(TextUtils.isMostlyCaps(text));
    }

    @ParameterizedTest
    @CsvSource({
            "a̐, true",
            "abc, false",
            "a̐b̐c̐, true",
            "a̐b, true",
            "a̐bcdefghijklmnopqrstuvwxyz, false",
            "a̐b̐c̐d̐e̐f̐, true",
            "a̐b̐c̐d̐e̐, true",
            "a̐b̐c, true",
            "abcd̐efgh, false"
    })
    void isZalgo_shouldReturnExpected(String text, boolean expected) {
        assertEquals(expected, TextUtils.isZalgo(text));
    }

    @Test
    void isZalgo_withEmptyString_returnsFalse() {
        assertFalse(TextUtils.isZalgo(""));
    }

    @Test
    void isZalgo_withNull_throwsNPE() {
        assertThrows(NullPointerException.class, () -> TextUtils.isZalgo(null));
    }

    @ParameterizedTest
    @CsvSource({
            "'😀', 1",
            "'😀😁', 2",
            "'😀abc', 1",
            "'abc', 0",
            "'❤️', 1",
            "'👨‍👩‍👦', 1",
            "'🇺🇦', 1",
            "'  😀  ', 1",
            "'', 0",
            "'   ', 0"
    })
    void countEmojis_shouldReturnExpected(String text, int expected) {
        assertEquals(expected, TextUtils.countEmojis(text));
    }

    @Test
    void countEmojis_withNull_throwsNPE() {
        assertThrows(NullPointerException.class, () -> TextUtils.countEmojis(null));
    }

    @Test
    void countEmojis_withMixedContent_shouldCountCorrectly() {
        String text = "Привет 😀 мир! 😁👍";
        assertEquals(3, TextUtils.countEmojis(text));
    }
}