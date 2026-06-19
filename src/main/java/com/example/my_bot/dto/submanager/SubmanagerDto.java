package com.example.my_bot.dto.submanager;

import com.vk.api.sdk.client.actors.GroupActor;
import jakarta.persistence.Column;
import lombok.*;


@Getter
public class SubmanagerDto {

    private final long groupId;
    private final String token;
    private final int serverId;
    private final String secretKey;
    private final GroupActor groupActor;


    public SubmanagerDto(long groupId, @NonNull String token, int serverId, @NonNull String secretKey){
        this.groupId = groupId;
        this.token = token;
        this.serverId = serverId;
        this.secretKey = secretKey;
        this.groupActor = new GroupActor(groupId, token);
    }
}
