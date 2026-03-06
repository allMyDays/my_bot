package com.example.my_bot.mapper;

import com.example.my_bot.dto.ChatDetailsDto;
import com.example.my_bot.dto.MemberWithRoleDto;
import com.example.my_bot.entity.ChatEntity;
import com.example.my_bot.entity.MemberEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public abstract class ChatMapper {

    public abstract ChatDetailsDto toChatDetailsDto(ChatEntity chatEntity);

}
