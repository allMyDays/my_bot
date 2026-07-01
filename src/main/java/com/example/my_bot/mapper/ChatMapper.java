package com.example.my_bot.mapper;

import com.example.my_bot.dto.chat.AdminChatDto;
import com.example.my_bot.dto.chat.ChatDetailsDto;
import com.example.my_bot.entity.AdminChatEntity;
import com.example.my_bot.entity.ChatEntity;
import org.jspecify.annotations.NonNull;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public abstract class ChatMapper {

    public abstract ChatDetailsDto toChatDetailsDto(@NonNull ChatEntity chatEntity);

    public abstract AdminChatDto toAdminChatDto(@NonNull AdminChatEntity entity);

}
