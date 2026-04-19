package com.example.my_bot.mapper;

import com.example.my_bot.dto.ChatDetailsDto;
import com.example.my_bot.dto.event.EventDto;
import com.example.my_bot.entity.ChatEntity;
import com.example.my_bot.entity.EventEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public abstract class EventMapper {

    public abstract EventDto toEventDto(EventEntity entity);

}
