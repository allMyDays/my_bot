package com.example.my_bot.mapper;

import com.example.my_bot.dto.limit.RoleLimitDto;
import com.example.my_bot.dto.member.MemberDto;
import com.example.my_bot.entity.MemberEntity;
import com.example.my_bot.entity.RoleLimitEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class LimitMapper {

    @Mapping(target = "entityId", source = "id")
    public abstract RoleLimitDto toRoleLimitDto(RoleLimitEntity entity);

    public abstract List<RoleLimitDto> toRoleLimitDto(List<RoleLimitEntity> entities);





}
