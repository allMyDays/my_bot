package com.example.my_bot.mapper;

import com.example.my_bot.dto.MemberWithRoleDto;
import com.example.my_bot.dto.RoleDto;
import com.example.my_bot.entity.MemberEntity;
import com.example.my_bot.entity.RoleEntity;
import com.example.my_bot.enumeration.DefaultRole;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.ArrayList;
import java.util.List;

@Mapper(componentModel = "spring")
public abstract class RoleMapper {

    @Mapping(target = "isRoleInDataBase", source = "isRoleInDataBase")
    public abstract RoleDto toDto(RoleEntity roleEntity,  boolean isRoleInDataBase);


    public List<RoleDto> toDto(List<RoleEntity> roleEntities,  boolean isRoleInDataBase){

        if(roleEntities==null ) return null;

        List<RoleDto> dtoList = new ArrayList<>();

        for(RoleEntity roleEntity: roleEntities){
            dtoList.add(toDto(roleEntity, isRoleInDataBase ));
        }
        return dtoList;

    }


    public RoleDto toDto(DefaultRole defaultRole,  boolean isRoleInDataBase){
        return new RoleDto(defaultRole.getRoleName(), defaultRole.getRolePriority(), isRoleInDataBase);
    }


}
