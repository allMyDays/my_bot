package com.example.my_bot.dto.permission;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class RoleCommandPermissionDto {

    private String commandName;

    private Integer rolePriority;

}
