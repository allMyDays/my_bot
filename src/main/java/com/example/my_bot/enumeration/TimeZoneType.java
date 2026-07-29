package com.example.my_bot.enumeration;

import lombok.Getter;
import lombok.NonNull;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum TimeZoneType {

    GMT_MINUS_12("GMT-12", -12),
    GMT_MINUS_11("GMT-11", -11),
    GMT_MINUS_10("GMT-10", -10),
    GMT_MINUS_9("GMT-9", -9),
    GMT_MINUS_8("GMT-8", -8),
    GMT_MINUS_7("GMT-7", -7),
    GMT_MINUS_6("GMT-6", -6),
    GMT_MINUS_5("GMT-5", -5),
    GMT_MINUS_4("GMT-4", -4),
    GMT_MINUS_3("GMT-3", -3),
    GMT_MINUS_2("GMT-2", -2),
    GMT_MINUS_1("GMT-1", -1),
    GMT_PLUS_0("GMT+0", 0),
    GMT_PLUS_1("GMT+1", 1),
    GMT_PLUS_2("GMT+2", 2),
    GMT_PLUS_3("GMT+3", 3),
    GMT_PLUS_4("GMT+4", 4),
    GMT_PLUS_5("GMT+5", 5),
    GMT_PLUS_6("GMT+6", 6),
    GMT_PLUS_7("GMT+7", 7),
    GMT_PLUS_8("GMT+8", 8),
    GMT_PLUS_9("GMT+9", 9),
    GMT_PLUS_10("GMT+10", 10),
    GMT_PLUS_11("GMT+11", 11),
    GMT_PLUS_12("GMT+12", 12),
    GMT_PLUS_13("GMT+13", 13),
    GMT_PLUS_14("GMT+14", 14);

    @Getter
    private final String stringType;
    private final int offset;
    private static final Map<String, TimeZoneType> stringTypeMAP =
            Arrays.stream(values())
                    .collect(Collectors.toMap(
                            z -> z.stringType.toLowerCase(),
                            Function.identity()
                    ));

    TimeZoneType(String stringType, int offset) {
        this.stringType = stringType;
        this.offset = offset;
    }

    public ZoneOffset getZoneOffset(){
        return ZoneOffset.ofHours(offset);
    }

    public static Optional<TimeZoneType> findZoneByStringType(@NonNull String type){
        return Optional.ofNullable(stringTypeMAP.get(type.trim().toLowerCase()));
    }
}
