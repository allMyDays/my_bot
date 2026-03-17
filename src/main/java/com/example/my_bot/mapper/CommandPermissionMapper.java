package com.example.my_bot.mapper;

import com.example.my_bot.dto.permission.RolePermissionDto;
import com.example.my_bot.entity.RolePermissionEntity;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class CommandPermissionMapper {

    public abstract RolePermissionDto toPermissionDto(RolePermissionEntity entity);

    public abstract List<RolePermissionDto> toPermissionDtoList(List<RolePermissionEntity> entities);





}
