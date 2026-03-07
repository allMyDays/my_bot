package com.example.my_bot.mapper;

import com.example.my_bot.dto.MemberWithRoleDto;
import com.example.my_bot.dto.RoleDto;
import com.example.my_bot.entity.MemberEntity;
import com.example.my_bot.entity.RoleEntity;
import com.example.my_bot.enumeration.DefaultRole;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class RoleMapper {

    public abstract RoleDto toDto(RoleEntity roleEntity);

    public abstract List<RoleDto> toDto(List<RoleEntity> roleEntities);

    public RoleDto toDto(DefaultRole defaultRole){
        return new RoleDto(defaultRole.getRoleName(), defaultRole.getRolePriority());
    }





}
