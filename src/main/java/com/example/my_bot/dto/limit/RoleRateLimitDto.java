package com.example.my_bot.dto.limit;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class RoleRateLimitDto {

    private long entityId;

    private String commandName;

    private int rolePriority;

    private boolean isPersonal;

    private int maxUsage;

    private int periodInSeconds;



}
