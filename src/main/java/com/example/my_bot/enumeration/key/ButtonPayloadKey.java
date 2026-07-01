package com.example.my_bot.enumeration.key;

import lombok.Getter;
import lombok.NonNull;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum ButtonPayloadKey {

    ADMIN_CHAT_EXECUTE_COMMAND_IN_ONE_BOUND_CHAT("a_ch_e_c_in_one_b_ch"),

    ADMIN_CHAT_EXECUTE_COMMAND_IN_ALL_BOUND_CHATS("a_ch_e_c_in_all_b_ch"),

    ADMIN_CHAT_EXECUTE_COMMAND_IN_THIS_ADMIN_CHAT("a_ch_e_c_in_this_a_ch");


    @Getter
    private final String stringValue;


    private static final Map<String, ButtonPayloadKey> stringValueMAP =
            Arrays.stream(values())
                    .collect(Collectors.toMap(
                            z -> z.stringValue.toLowerCase(),
                            Function.identity()
                    ));


    ButtonPayloadKey(@NonNull String value) {
        this.stringValue = value;
    }

    public static Optional<ButtonPayloadKey> findKeyByStringValue(@NonNull String value){
        return Optional.ofNullable(stringValueMAP.get(value.trim().toLowerCase()));
    }

}
