package com.example.my_bot.dto.permission;

import com.example.my_bot.dto.RoleDto;
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
public class MemberPermissionSettingResult {

    private Set<String> notFound=new HashSet<>();

    private Set<String> accepted =new HashSet<>();

    private Set<String> hasRequiredPermissionAlready=new HashSet<>();

    private Set<String> forbiddenToEdit=new HashSet<>();

    private Set<String> notEnoughSpaceToAddNew=new HashSet<>();


}
