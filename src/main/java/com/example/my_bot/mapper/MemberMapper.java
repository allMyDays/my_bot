package com.example.my_bot.mapper;

import com.example.my_bot.dto.member.MemberWithRoleDto;
import com.example.my_bot.entity.MemberEntity;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class MemberMapper {

    public abstract MemberWithRoleDto toMemberWithRoleDto(MemberEntity memberEntity);

    public abstract List<MemberWithRoleDto> toMemberWithRoleDtoList(List<MemberEntity> memberEntities);





}
