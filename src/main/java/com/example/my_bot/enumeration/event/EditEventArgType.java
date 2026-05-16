package com.example.my_bot.enumeration.event;

import lombok.Getter;
import lombok.NonNull;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
public enum EditEventArgType {

    ACTION_LIMIT("лимитдействия"),
    DAILY_WORK_TIME("времяработы"),
    COOLDOWN("кулдаун"),
    EXCEPTIONAL_MEMBER("исключение"),
    NEW_MEMBERS("новички"),
    PERSONAL_EVENT("толькодля"),
    ROLE("роль"),
    COMMAND("команда");

    @Getter
    private final String cyrillicType;

    private static final Map<String, EditEventArgType> cyrillicTypeMAP =
            Arrays.stream(values())
                    .collect(Collectors.toMap(
                            z -> z.cyrillicType.toLowerCase(),
                            Function.identity()
                    ));


    EditEventArgType(String cyrillicType){
        this.cyrillicType = cyrillicType;
    }

    public static Optional<EditEventArgType> findByCyrillicType(@NonNull String type){
        return Optional.ofNullable(cyrillicTypeMAP.get(type.trim().toLowerCase()));
    }




}
