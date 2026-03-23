package com.example.my_bot.mapper;

import com.example.my_bot.dto.limit.RoleRateLimitDto;
import com.example.my_bot.entity.RoleRateLimitEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class RateLimitMapper {

    @Mapping(target = "entityId", source = "id")
    public abstract RoleRateLimitDto toRoleLimitDto(RoleRateLimitEntity entity);

    public abstract List<RoleRateLimitDto> toRoleLimitDto(List<RoleRateLimitEntity> entities);





}
