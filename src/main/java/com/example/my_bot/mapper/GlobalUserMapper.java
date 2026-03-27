package com.example.my_bot.mapper;

import com.example.my_bot.dto.user.GlobalUserDetailsDto;
import com.example.my_bot.entity.GlobalUserEntity;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class GlobalUserMapper {

    public abstract GlobalUserDetailsDto toUserDetailsDto(GlobalUserEntity globalUserEntity);

    public abstract List<GlobalUserDetailsDto> toUserDetailsDto(List<GlobalUserEntity> globalUserEntity);





}
