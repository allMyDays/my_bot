package com.example.my_bot.utils;

import lombok.Getter;
import lombok.NonNull;

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
         * Возвращает количество секунд для указанной единицы времени.
         *
         * @param unit строка с единицей времени (например, "секунды", "минута", "часов")
         * @return количество секунд в optional, optional пуст если единица времени не распознана
         */
        public static Optional<Integer> toSeconds(String unit) {
            String normalized = unit.trim().toLowerCase();
            for (TimeUnit tu : TimeUnit.values()) {
                if (tu.keywords.contains(normalized)) {
                    return Optional.of(tu.seconds);
                }
            }
            return Optional.empty();
        }
    }



