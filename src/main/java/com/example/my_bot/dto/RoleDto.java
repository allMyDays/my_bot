package com.example.my_bot.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Objects;


@Getter
//@Setter
@AllArgsConstructor
public class RoleDto {

    private final String roleName;

    private final int rolePriority;

    private final boolean isRoleInDataBase;




    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        RoleDto roleDto = (RoleDto) o;
        return rolePriority == roleDto.rolePriority;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(rolePriority);
    }

}
