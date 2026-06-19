package com.example.my_bot.vk.enumeration;

import lombok.Getter;
import lombok.NonNull;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum GroupTokenPermissionType {

    PHOTOS("photos","фотографии"),
    DOCS("docs", "файлы"),
    MESSAGES("messages", "сообщения сообщества"),
    WALL("wall", "стена"),
    COMMUNITY_MANAGEMENT("manage", "управление сообществом"),
    STORIES("stories", "истории"),
    MARKET("market", "товары");

    @Getter
    private final String vkType;

    @Getter
    private final String cyrillicName;

    private static final Map<String, GroupTokenPermissionType> vkTypeMAP =
            Arrays.stream(values())
                    .collect(Collectors.toMap(
                            z -> z.getVkType().toLowerCase(),
                            Function.identity()
                    ));

    GroupTokenPermissionType(@NonNull String vkType, @NonNull String cyrillicName) {
        this.vkType = vkType;
        this.cyrillicName = cyrillicName;
    }

    public static Optional<GroupTokenPermissionType> findByVkType(@NonNull String type){
        return Optional.ofNullable(vkTypeMAP.get(type.trim().toLowerCase()));
    }




}
