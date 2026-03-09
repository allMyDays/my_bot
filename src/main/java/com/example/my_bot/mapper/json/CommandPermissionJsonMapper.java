package com.example.my_bot.mapper.json;


import com.example.my_bot.dto.permission.RoleCommandPermissionDto;
import com.example.my_bot.exception.JSONDeserializationException;
import com.example.my_bot.exception.JSONSerializationException;
import lombok.extern.slf4j.Slf4j;
import org.mapstruct.Mapper;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Mapper(componentModel = "spring")
@Slf4j
public abstract class CommandPermissionJsonMapper {
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String toJson(RoleCommandPermissionDto dto) {
       try{
           return objectMapper.writeValueAsString(dto);
       }catch (Exception e){
           throw new JSONSerializationException(RoleCommandPermissionDto.class, e);
       }
    }

    public RoleCommandPermissionDto fromJson(String json) {
        try{
            return objectMapper.readValue(json, RoleCommandPermissionDto.class);
        }catch (Exception e){
            throw new JSONDeserializationException(RoleCommandPermissionDto.class, json, e);


        }
    }

    public abstract List<RoleCommandPermissionDto> fromJson(List<String> json);



}

    

