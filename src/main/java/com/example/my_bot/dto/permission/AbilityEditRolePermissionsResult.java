package com.example.my_bot.dto.permission;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;


@Getter
@AllArgsConstructor
@NoArgsConstructor
@Setter
public class AbilityEditRolePermissionsResult {

    private Set<String> allowed=new HashSet<>();

    private Set<String> forbidden=new HashSet<>();
}
