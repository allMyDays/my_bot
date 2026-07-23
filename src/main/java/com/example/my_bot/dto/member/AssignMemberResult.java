package com.example.my_bot.dto.member;

import com.example.my_bot.dto.RoleDto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Setter
@Getter
public class AssignMemberResult {

    RoleDto previousRole;

    RoleDto newRole;
}
