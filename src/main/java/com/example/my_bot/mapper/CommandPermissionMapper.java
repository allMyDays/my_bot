package com.example.my_bot.mapper;

import com.example.my_bot.dto.permission.RolePermissionDto;
import com.example.my_bot.entity.CommandPermissionEntity;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class CommandPermissionMapper {

    public abstract RolePermissionDto toPermissionDto(CommandPermissionEntity entity);

    public abstract List<RolePermissionDto> toPermissionDtoList(List<CommandPermissionEntity> entities);





}
