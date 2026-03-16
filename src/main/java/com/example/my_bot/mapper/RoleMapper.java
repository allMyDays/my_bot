package com.example.my_bot.mapper;

import com.example.my_bot.dto.RoleDto;
import com.example.my_bot.entity.RoleEntity;
import com.example.my_bot.enumeration.DefaultRole;
import lombok.NonNull;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.ArrayList;
import java.util.List;

@Mapper(componentModel = "spring")
public abstract class RoleMapper {


    public abstract RoleDto toDto(RoleEntity roleEntity);


    public List<RoleDto> toDto(@NonNull List<RoleEntity> roleEntities){
        List<RoleDto> resultList = new ArrayList<>();
        for(RoleEntity roleEntity: roleEntities){
            resultList.add(toDto(roleEntity));
        }return resultList;
    }


    public RoleDto toDto(DefaultRole defaultRole){
        return new RoleDto(defaultRole.getRoleName(), defaultRole.getRolePriority());
    }


}
