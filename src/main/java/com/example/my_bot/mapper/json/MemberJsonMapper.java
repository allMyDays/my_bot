package com.example.my_bot.mapper.json;


import com.example.my_bot.dto.ChatDetailsDto;
import com.example.my_bot.dto.MemberWithRoleDto;
import com.example.my_bot.exception.JSONDeserializationException;
import com.example.my_bot.exception.JSONSerializationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.mapstruct.Mapper;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Mapper(componentModel = "spring")
@Slf4j
public abstract class MemberJsonMapper {
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String toJson(MemberWithRoleDto dto) {
       try{
           return objectMapper.writeValueAsString(dto);
       }catch (Exception e){
           throw new JSONSerializationException(MemberWithRoleDto.class, e);
       }
    }

    public MemberWithRoleDto fromJson(String json) {
        try{
            return objectMapper.readValue(json, MemberWithRoleDto.class);
        }catch (Exception e){
            throw new JSONDeserializationException(MemberWithRoleDto.class, json, e);


        }
    }

    public abstract List<MemberWithRoleDto> fromJson(List<String> json);



}

    

