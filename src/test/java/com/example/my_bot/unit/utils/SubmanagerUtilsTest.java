package com.example.my_bot.unit.utils;

import com.example.my_bot.utils.SubmanagerUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class SubmanagerUtilsTest {

    @Test
    void generateNewBindingCode_shouldReturnStringMatchingPattern() {
        long submanagerId = 12345;
        String code = SubmanagerUtils.generateNewBindingCode(submanagerId);
        assertTrue(SubmanagerUtils.stringMatchesABindingCode(code));
        assertTrue(code.startsWith("[club12345|"));
    }

    @Test
    void generateNewBindingCode_withNegativeId_shouldUseAbsoluteValue() {
        long submanagerId = -6789;
        String code = SubmanagerUtils.generateNewBindingCode(submanagerId);
        assertTrue(code.startsWith("[club6789|"));
        assertTrue(SubmanagerUtils.stringMatchesABindingCode(code));
    }

    @Test
    void generateNewBindingCode_withZeroId_shouldWork() {
        long submanagerId = 0;
        String code = SubmanagerUtils.generateNewBindingCode(submanagerId);
        assertTrue(code.startsWith("[club0|"));
        assertTrue(SubmanagerUtils.stringMatchesABindingCode(code));
    }

    @Test
    void generateNewBindingCode_withMaxLong_shouldStartWithCorrectPrefix() {
        long submanagerId = Long.MAX_VALUE;
        String code = SubmanagerUtils.generateNewBindingCode(submanagerId);
        assertTrue(code.startsWith("[club" + Long.MAX_VALUE + "|"));
        assertFalse(SubmanagerUtils.stringMatchesABindingCode(code));
    }

    @Test
    void generateNewBindingCode_withMinLong_shouldStartWithCorrectPrefix() {
        long submanagerId = Long.MIN_VALUE;
        String code = SubmanagerUtils.generateNewBindingCode(submanagerId);
        assertTrue(code.startsWith("[club" + Long.MIN_VALUE + "|"));
        assertFalse(SubmanagerUtils.stringMatchesABindingCode(code));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "[club123|550e8400-e29b-41d4-a716-446655440000]",
            "[club1|12345678-1234-1234-1234-123456789abc]",
            "[club1234567890|550e8400-e29b-41d4-a716-446655440000]",
            "[club0|00000000-0000-0000-0000-000000000000]"
    })
    void stringMatchesABindingCode_withValidStrings_shouldReturnTrue(String str) {
        assertTrue(SubmanagerUtils.stringMatchesABindingCode(str));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "[club123|550e8400-e29b-41d4-a716-44665544000]", // слишком короткий UUID
            "[club123|550e8400-e29b-41d4-a716-446655440000a]", // слишком длинный
            "[club123|550e8400-e29b-41d4-a716-44665544000X]", // не hex
            "[club123|550e8400-e29b-41d4-a716-446655440000", // нет закрывающей скобки
            "club123|550e8400-e29b-41d4-a716-446655440000]", // нет открывающей скобки
            "[club|550e8400-e29b-41d4-a716-446655440000]", // нет цифр после club
            "[club12a|550e8400-e29b-41d4-a716-446655440000]", // буквы в цифрах
            "[club12345678901|550e8400-e29b-41d4-a716-446655440000]", // более 10 цифр
            "[club-123|550e8400-e29b-41d4-a716-446655440000]" // отрицательное число
    })
    void stringMatchesABindingCode_withInvalidStrings_shouldReturnFalse(String str) {
        assertFalse(SubmanagerUtils.stringMatchesABindingCode(str));
    }

    @Test
    void stringMatchesABindingCode_withNull_throwsNPE() {
        assertThrows(NullPointerException.class, () -> SubmanagerUtils.stringMatchesABindingCode(null));
    }
}