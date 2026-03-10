package com.example.my_bot.mapper.json;


import com.example.my_bot.dto.permission.RolePermissionDto;
import com.example.my_bot.exception.json.JSONDeserializationException;
import com.example.my_bot.exception.json.JSONSerializationException;
import lombok.extern.slf4j.Slf4j;
import org.mapstruct.Mapper;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Mapper(componentModel = "spring")
@Slf4j
public abstract class CommandPermissionJsonMapper {
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String toJson(RolePermissionDto dto) {
       try{
           return objectMapper.writeValueAsString(dto);
       }catch (Exception e){
           throw new JSONSerializationException(RolePermissionDto.class, e);
       }
    }

    public RolePermissionDto fromJson(String json) {
        try{
            return objectMapper.readValue(json, RolePermissionDto.class);
        }catch (Exception e){
            throw new JSONDeserializationException(RolePermissionDto.class, json, e);


        }
    }

    public abstract List<RolePermissionDto> fromJson(List<String> json);



}

    

