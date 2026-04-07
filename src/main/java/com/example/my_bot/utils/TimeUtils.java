package com.example.my_bot.utils;

import com.example.my_bot.enumeration.TimeZoneType;
import lombok.Getter;
import lombok.NonNull;

import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public class TimeUtils {

    private static final DateTimeFormatter RUSSIAN_DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("dd MMMM yyyy в HH:mm", new Locale("ru"));
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    @Getter
    private enum TimeUnit {
        SECOND(1, "секунд", "секунда", "секунды", "секунду","сек"),
        MINUTE(60, "минут", "минута", "минуты", "минуту","мин"),
        HOUR(3600, "час", "часа", "часов","ч"),
        DAY(86_400, "день", "дня", "дней","дн"),
        MONTH(2_592_000, "месяц", "месяца", "месяцев","мес");

        private final int seconds;
        private final Set<String> keywords;

        TimeUnit(int seconds, String... keywords) {
            this.seconds = seconds;
            this.keywords = Set.of(keywords);
        }
    }

    public static String formatDurationFromSeconds(long seconds, boolean includeSeconds) {
        if (seconds < 0) throw new IllegalArgumentException("seconds must be >= 0");
        if (seconds == 0) return includeSeconds ? "0 секунд." : "";

        long[] limits = {2592000, 604800, 86400, 3600, 60, 1};
        String[] names = {"мес.", "нед.", "дн.", "ч.", "мин", "сек."};
        int maxIndex = includeSeconds ? 5 : 4;

        StringBuilder sb = new StringBuilder();
        long remaining = seconds;

        for (int i = 0; i <= maxIndex; i++) {
            long value = remaining / limits[i];
            if (value > 0) {
                sb.append(value).append(" ").append(names[i]);
                remaining %= limits[i];
                if (i < maxIndex && remaining > 0) {
                    sb.append(" ");
                }
            }
        }
        String result = sb.toString();
        if (!result.endsWith(".") && !result.isEmpty()) {
            result += ".";
        }
        return result;
    }
        /**
         * Возвращает количество секунд для указанной единицы времени и числового значения. например: 3 часа, 3 дня и т.д.
         *
         * @param unit строка с единицей времени (например, "секунды", "минута", "часов")
         * @param num строка-число, которое умножается на единицу времени, переведенную в секунды
         * @return количество секунд в optional, optional пуст если единица времени не распознана, некорректное число, или некорректно большой или отрицательный период
         */
        public static Optional<Long> toSecondsFromString(String num, String unit) {
            if (num == null || unit == null) {
                return Optional.empty();
            }

            String normalizedUnit = unit.trim().toLowerCase();
            TimeUnit foundTimeUnit = null;

            for (TimeUnit tu : TimeUnit.values()) {
                if (tu.keywords.contains(normalizedUnit)) {
                    foundTimeUnit = tu;
                }
            }

            if (foundTimeUnit == null) {
                return Optional.empty();
            }

            BigInteger number;
            try {
                number = new BigInteger(num);
            } catch (Exception e) {
                return Optional.empty();
            }

            if (number.signum() < 0) {
                return Optional.empty();
            }

            BigInteger seconds = BigInteger.valueOf(foundTimeUnit.seconds).multiply(number);
            try {
                long result = seconds.longValueExact();
                return Optional.of(result);
            } catch (Exception e) {
                return Optional.empty();
            }
        }

    public static String getStringDateTimeWithTimeZone(@NonNull Instant instant, @NonNull TimeZoneType timeZone) {
       LocalDateTime localDateTime = instant.atZone(timeZone.getZoneOffset()).toLocalDateTime();
        return localDateTime.format(RUSSIAN_DATE_TIME_FORMATTER)+" "+timeZone.getStringType();
    }

    public static String getStringDateTimeWithTimeZone(@NonNull LocalDateTime localDateTime, @NonNull TimeZoneType timeZone) {
        return localDateTime.format(RUSSIAN_DATE_TIME_FORMATTER)+" "+timeZone.getStringType();
    }

    /**
     * Парсит строку времени в формате "HH:mm" (например, "07:30").
     *
     * @param timeStr строка с временем
     * @return Optional, содержащий LocalTime, если парсинг успешен, иначе пустой Optional
     */
    public static Optional<LocalTime> parseTimeOfDay(@NonNull String timeStr) {
        try {
            LocalTime time = LocalTime.parse(timeStr, TIME_FORMATTER);
            return Optional.of(time);
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }
    /**
     * Преобразует строку формата "часы:минуты" в общее количество секунд.
     * <p>
     * Пример: "877:30" → 877 * 3600 + 30 * 60 = 3_159_000 секунд.
     * </p>
     *
     * @param timeStr строка с временем, например "877:30"
     * @return Optional с количеством секунд, если строка корректна, иначе пустой Optional
     */
    public static Optional<Long> parseManyHoursWithMinutes(@NonNull String timeStr) {

        String[] parts = timeStr.split(":");
        if (parts.length != 2) return Optional.empty();

        try {
            long hours = Long.parseLong(parts[0].trim());
            long minutes = Long.parseLong(parts[1].trim());

            if (hours < 0 || minutes < 0 || minutes >= 60) {
                return Optional.empty();
            }

            long totalSeconds = hours * 3600 + minutes * 60;

            return Optional.of(totalSeconds);

        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Парсит строку с датой и временем в формате "HH:mm dd.MM.yyyy".
     * <p>
     * Пример: "00:00 01.01.2027" → LocalDateTime.of(2027, 1, 1, 0, 0, 0)
     * </p>
     *
     * @param dateTimeStr строка с датой и временем
     * @return Optional, содержащий LocalDateTime при успешном парсинге, иначе пустой Optional
     */
    public static Optional<LocalDateTime> parseDateTime(@NonNull String dateTimeStr) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm dd.MM.yyyy");
            return Optional.of(LocalDateTime.parse(dateTimeStr, formatter));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }








    }



