package com.example.my_bot.utils;

import lombok.Getter;

import java.math.BigInteger;
import java.util.Optional;
import java.util.Set;

public class TimeUtils {

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

    public static String formatDuration(long seconds, boolean includeSeconds) {
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
    }



