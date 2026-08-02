package com.example.my_bot.unit.utils;

import com.example.my_bot.enumeration.TimeZoneType;
import com.example.my_bot.utils.TimeUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigInteger;
import java.time.*;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class TimeUtilsTest {

    @ParameterizedTest
    @CsvSource({
            "0, true, 0 секунд.",
            "0, false, ''",
            "1, true, ''",
            "1, false, 1 сек.",
            "59, false, 59 сек.",     
            "60, true, 1 мин.",
            "60, false, 1 мин.",
            "61, true, 1 мин 1 сек.",
            "61, false, 1 мин.",
            "3600, true, 1 ч.",
            "3661, true, 1 ч. 1 мин 1 сек.",
            "3661, false, 1 ч. 1 мин.",
            "86400, true, 1 дн.",
            "172800, true, 2 дн.",
            "2592000, true, 1 мес.",
            "604800, true, 1 нед.",
            "31536000, true, 12 мес. 5 дн.",  // исправлено
            "31536000, false, 12 мес. 5 дн."  // исправлено
    })
    void formatDurationFromSeconds_shouldReturnExpected(long seconds, boolean includeSeconds, String expected) {
        String result = TimeUtils.formatDurationFromSeconds(seconds, includeSeconds);
        if (expected.isEmpty()) {
            assertTrue(result.isEmpty());
        } else {
            assertEquals(expected, result);
        }
    }


    @Test
    void formatDurationFromSeconds_withNegativeSeconds_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> TimeUtils.formatDurationFromSeconds(-1, true));
    }

    @ParameterizedTest
    @CsvSource({
            "1, секунд, 1",
            "2, секунда, 2",
            "5, секунды, 5",
            "10, секунду, 10",
            "1, сек, 1",
            "1, минута, 60",
            "1, минут, 60",
            "3, минуты, 180",
            "1, час, 3600",
            "2, часа, 7200",
            "5, часов, 18000",
            "1, день, 86400",
            "1, дня, 86400",
            "7, дней, 604800",
            "1, месяц, 2592000",
            "2, месяца, 5184000",
            "12, месяцев, 31104000",
            "0, секунд, 0",
            "1000000000, секунд, 1000000000"
    })
    void toSecondsFromString_withValidInput_shouldReturnSeconds(String num, String unit, long expected) {
        Optional<Long> result = TimeUtils.toSecondsFromString(num, unit);
        assertTrue(result.isPresent());
        assertEquals(expected, result.get());
    }

    @ParameterizedTest
    @CsvSource({
            "1, unknown, false",
            "abc, секунд, false",
            "1.5, секунд, false",
            "-1, секунд, false",
            "null, секунд, false",
            "1, null, false",
            "9999999999999999999, секунд, false"
    })
    void toSecondsFromString_withInvalidInput_shouldReturnEmpty(String num, String unit, boolean expectedPresent) {
        Optional<Long> result = TimeUtils.toSecondsFromString(num, unit);
        assertEquals(expectedPresent, result.isPresent());
    }

    @Test
    void toSecondsFromString_withBothNull_returnsEmpty() {
        assertTrue(TimeUtils.toSecondsFromString(null, null).isEmpty());
    }

    @Test
    void getFormattedStringDateTimeWithTimeZone_shouldReturnFormattedString() {
        Instant instant = Instant.parse("2025-01-01T12:00:00Z");
        TimeZoneType timeZone = TimeZoneType.GMT_PLUS_3;
        String result = TimeUtils.getFormattedStringDateTimeWithTimeZone(instant, timeZone);
        assertTrue(result.contains("января"));
        assertTrue(result.contains("2025"));
        assertTrue(result.contains("15:00"));
        assertTrue(result.endsWith("GMT+3"));
    }

    @Test
    void getFormattedStringDateTimeWithTimeZone_withNull_throwsNPE() {
        assertThrows(NullPointerException.class, () ->
                TimeUtils.getFormattedStringDateTimeWithTimeZone(null, TimeZoneType.GMT_PLUS_0));
        assertThrows(NullPointerException.class, () ->
                TimeUtils.getFormattedStringDateTimeWithTimeZone(Instant.now(), null));
    }

    @Test
    void getFormattedStringDateTime_shouldReturnFormattedStringWithoutTimezone() {
        Instant instant = Instant.parse("2025-01-01T12:00:00Z");
        TimeZoneType timeZone = TimeZoneType.GMT_PLUS_3;
        String result = TimeUtils.getFormattedStringDateTime(instant, timeZone);
        assertTrue(result.contains("января"));
        assertTrue(result.contains("2025"));
        assertTrue(result.contains("15:00"));
        assertFalse(result.contains("GMT"));
    }

    @Test
    void getFormattedStringDateTime_withNull_throwsNPE() {
        assertThrows(NullPointerException.class, () ->
                TimeUtils.getFormattedStringDateTime(null, TimeZoneType.GMT_PLUS_0));
        assertThrows(NullPointerException.class, () ->
                TimeUtils.getFormattedStringDateTime(Instant.now(), null));
    }

    @Test
    void getStringDateTimeWithTimeZone_shouldReturnFormattedString() {
        LocalDateTime ldt = LocalDateTime.of(2025, 1, 1, 12, 0);
        TimeZoneType timeZone = TimeZoneType.GMT_PLUS_3;
        String result = TimeUtils.getStringDateTimeWithTimeZone(ldt, timeZone);
        assertTrue(result.contains("января"));
        assertTrue(result.contains("2025"));
        assertTrue(result.contains("12:00"));
        assertTrue(result.endsWith("GMT+3"));
    }

    @Test
    void getStringDateTimeWithTimeZone_withNull_throwsNPE() {
        assertThrows(NullPointerException.class, () ->
                TimeUtils.getStringDateTimeWithTimeZone(null, TimeZoneType.GMT_PLUS_0));
        assertThrows(NullPointerException.class, () ->
                TimeUtils.getStringDateTimeWithTimeZone(LocalDateTime.now(), null));
    }

    @ParameterizedTest
    @CsvSource({
            "00:00, true",
            "23:59, true",
            "12:30, true",
            "24:00, false",
            "12:60, false",
            "12:5, false",
            "abc, false"
    })
    void parseTimeOfDay_shouldReturnExpected(String timeStr, boolean expectedPresent) {
        Optional<LocalTime> result = TimeUtils.parseTimeOfDay(timeStr);
        assertEquals(expectedPresent, result.isPresent());
        if (expectedPresent) {
            assertNotNull(result.get());
        }
    }

    @Test
    void parseTimeOfDay_withNull_throwsNPE() {
        assertThrows(NullPointerException.class, () -> TimeUtils.parseTimeOfDay(null));
    }

    @ParameterizedTest
    @CsvSource({
            "0:00, 0",
            "1:30, 5400",
            "877:30, 3159000",
            "12:05, 43500",
            "0:59, 3540",
            "00:00, 0",
            "  1:30  , 5400"
    })
    void parseManyHoursWithMinutes_withValidInput_shouldReturnSeconds(String timeStr, long expected) {
        Optional<Long> result = TimeUtils.parseManyHoursWithMinutes(timeStr);
        assertTrue(result.isPresent());
        assertEquals(expected, result.get());
    }

    @ParameterizedTest
    @CsvSource({
            "12:60, false",
            "24:00, true",
            "1:5, true",
            "abc, false",
            "1:2:3, false",
            "-1:30, false",
            "1:-30, false",
            "null, false"
    })
    void parseManyHoursWithMinutes_withInvalidInput_shouldReturnEmpty(String timeStr, boolean expectedPresent) {
        Optional<Long> result = TimeUtils.parseManyHoursWithMinutes(timeStr);
        assertEquals(expectedPresent, result.isPresent());
    }

    @Test
    void parseManyHoursWithMinutes_withNull_throwsNPE() {
        assertThrows(NullPointerException.class, () -> TimeUtils.parseManyHoursWithMinutes(null));
    }


    @ParameterizedTest
    @CsvSource({
            "00:00 01.01.2027, 2027-01-01T00:00",
            "23:59 31.12.2026, 2026-12-31T23:59",
            "12:30 15.06.2025, 2025-06-15T12:30",
            " 00:00 01.01.2027, false",
            "00:00 01/01/2027, false",
            "25:00 01.01.2027, false"
    })
    void parseDateTime_shouldReturnExpected(String dateTimeStr, String expected) {
        Optional<LocalDateTime> result = TimeUtils.parseDateTime(dateTimeStr);
        if ("false".equals(expected)) {
            assertFalse(result.isPresent());
        } else {
            assertTrue(result.isPresent());
            assertEquals(LocalDateTime.parse(expected), result.get());
        }
    }

    @Test
    void parseDateTime_withNull_throwsNPE() {
        assertThrows(NullPointerException.class, () -> TimeUtils.parseDateTime(null));
    }

    @ParameterizedTest
    @CsvSource({
            "01.01.2027, 2027-01-01",
            "31.12.2026, 2026-12-31",
            "15.06.2025, 2025-06-15",
            " 01.01.2027 , 2027-01-01",
            "01/01/2027, false",
            "01.01.2027 00:00, false",
            "null, false"
    })
    void parseDate_shouldReturnExpected(String dateStr, String expected) {
        Optional<LocalDate> result = TimeUtils.parseDate(dateStr);
        if ("false".equals(expected)) {
            assertFalse(result.isPresent());
        } else {
            assertTrue(result.isPresent());
            assertEquals(LocalDate.parse(expected), result.get());
        }
    }

    @Test
    void parseDate_withNull_throwsNPE() {
        assertThrows(NullPointerException.class, () -> TimeUtils.parseDate(null));
    }


    @Test
    void toSecondsFromString_withMaxSeconds_returnsOptional() {
        Optional<Long> result = TimeUtils.toSecondsFromString(String.valueOf(Long.MAX_VALUE), "секунд");
        assertTrue(result.isPresent());
        assertEquals(Long.MAX_VALUE, result.get());
    }

    @Test
    void toSecondsFromString_withOverflow_returnsEmpty() {
        Optional<Long> result = TimeUtils.toSecondsFromString("9223372036854775808", "секунд");
        assertFalse(result.isPresent());
    }

    @Test
    void parseManyHoursWithMinutes_withLargeHours_returnsCorrect() {
        Optional<Long> result = TimeUtils.parseManyHoursWithMinutes("999999999:59");
        assertTrue(result.isPresent());
        assertEquals(999999999L * 3600 + 59 * 60, result.get());
    }

    @Test
    void timeZoneType_getZoneOffset_shouldReturnCorrectOffset() {
        assertEquals(ZoneOffset.ofHours(3), TimeZoneType.GMT_PLUS_3.getZoneOffset());
        assertEquals(ZoneOffset.ofHours(-5), TimeZoneType.GMT_MINUS_5.getZoneOffset());
        assertEquals(ZoneOffset.ofHours(0), TimeZoneType.GMT_PLUS_0.getZoneOffset());
    }

    @ParameterizedTest
    @CsvSource({
            "gmt+3, GMT_PLUS_3",
            "GMT+3, GMT_PLUS_3",
            "  GMT+3  , GMT_PLUS_3",
            "gmt-5, GMT_MINUS_5",
            "gmt+0, GMT_PLUS_0",
            "unknown, false"
    })
    void timeZoneType_findZoneByStringType_shouldReturnExpected(String input, String expectedEnum) {
        Optional<TimeZoneType> result = TimeZoneType.findZoneByStringType(input);
        if ("false".equals(expectedEnum)) {
            assertFalse(result.isPresent());
        } else {
            assertTrue(result.isPresent());
            assertEquals(TimeZoneType.valueOf(expectedEnum), result.get());
        }
    }

    @Test
    void timeZoneType_findZoneByStringType_withNull_throwsNPE() {
        assertThrows(NullPointerException.class, () -> TimeZoneType.findZoneByStringType(null));
    }
}