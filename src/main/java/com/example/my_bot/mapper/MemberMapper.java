package com.example.my_bot.mapper;

import com.example.my_bot.dto.member.MemberDto;
import com.example.my_bot.entity.MemberEntity;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class MemberMapper {

    public abstract MemberDto toMemberDto(MemberEntity memberEntity);

    public abstract List<MemberDto> toMemberDtoList(List<MemberEntity> memberEntities);





}
