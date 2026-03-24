package com.example.my_bot.mapper;

import com.example.my_bot.dto.ChatDetailsDto;
import com.example.my_bot.dto.user.UserDetailsDto;
import com.example.my_bot.entity.UserEntity;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class UserMapper {

    public abstract UserDetailsDto toUserDetailsDto(UserEntity userEntity);

    public abstract List<UserDetailsDto> toUserDetailsDto(List<UserEntity> userEntity);





}
