package com.example.my_bot.mapper.json;


import com.example.my_bot.dto.ChatDetailsDto;
import com.example.my_bot.dto.MemberWithRoleDto;
import com.example.my_bot.exception.JSONDeserializationException;
import com.example.my_bot.exception.JSONSerializationException;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.mapstruct.Mapper;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

@Mapper(componentModel = "spring")
@Slf4j
public abstract class ChatJsonMapper {
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());


    public String toJson(ChatDetailsDto dto) {
       try{
           return objectMapper.writeValueAsString(dto);
       }catch (Exception e){
           throw new JSONSerializationException(ChatDetailsDto.class, e);
       }
    }

    public ChatDetailsDto fromJson(String json) {
        try{
            return objectMapper.readValue(json, ChatDetailsDto.class);
        }catch (Exception e){
            throw new JSONDeserializationException(ChatDetailsDto.class, json, e);


        }
    }




}

    

