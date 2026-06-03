package com.example.my_bot.mapper;

import com.example.my_bot.dto.ChatDetailsDto;
import com.example.my_bot.dto.user.UserFullNameInEachCase;
import com.example.my_bot.entity.ChatEntity;
import com.example.my_bot.entity.GlobalUserEntity;
import com.example.my_bot.enumeration.user.NameCase;
import com.vk.api.sdk.objects.groups.GroupFull;
import com.vk.api.sdk.objects.users.UserFull;
import lombok.NonNull;
import org.mapstruct.Mapper;

import java.util.*;

@Mapper(componentModel = "spring")
public abstract class FullNameMapper {

    public GlobalUserEntity mapNames(@NonNull GlobalUserEntity entity, @NonNull UserFullNameInEachCase fullName){
        if(fullName.getNominative()!= null){
            entity.setFullNameInNom(fullName.getNominative());
        }
        if(fullName.getAccusative()!=null){
            entity.setFullNameInAcc(fullName.getAccusative());
        }
        if(fullName.getDative()!= null){
            entity.setFullNameInDat(fullName.getDative());
        }
        if(fullName.getInstrumental()!= null){
            entity.setFullNameInIns(fullName.getInstrumental());
        }
        if(fullName.getPrepositional()!= null){
            entity.setFullNameInAbl(fullName.getPrepositional());
        }
        if(fullName.getGenitive()!= null){
            entity.setFullNameInGen(fullName.getGenitive());
        }
        return entity;
    }

    public String mapNames(@NonNull GlobalUserEntity entity, @NonNull NameCase nameCase){
        return switch(nameCase){
            case NOMINATIVE -> entity.getFullNameInNom();
            case GENITIVE -> entity.getFullNameInGen();
            case DATIVE -> entity.getFullNameInDat();
            case ACCUSATIVE -> entity.getFullNameInAcc();
            case INSTRUMENTAL -> entity.getFullNameInIns();
            case PREPOSITIONAL -> entity.getFullNameInAbl();
        };
    }

    public List<UserFullNameInEachCase> mapProfileNames(@NonNull List<UserFull> userFullList){

        List<UserFullNameInEachCase> resultToReturn = new ArrayList<>();

        for(UserFull userFull: userFullList){
            UserFullNameInEachCase fullName = new UserFullNameInEachCase();
            fullName.setUserId(userFull.getId());

            if(userFull.getFirstNameNom()!=null&&userFull.getLastNameNom()!=null){
                fullName.setNominative(userFull.getFirstNameNom()+" "+userFull.getLastNameNom());
            }
            if(userFull.getFirstNameAcc()!=null&&userFull.getLastNameAcc()!=null){
                fullName.setAccusative(userFull.getFirstNameAcc()+" "+userFull.getLastNameAcc());
            }
            if(userFull.getFirstNameIns()!=null&&userFull.getLastNameIns()!=null){
                fullName.setInstrumental(userFull.getFirstNameIns()+" "+userFull.getLastNameIns());
            }
            if(userFull.getFirstNameGen()!=null&&userFull.getLastNameGen()!=null){
                fullName.setGenitive(userFull.getFirstNameGen()+" "+userFull.getLastNameGen());
            }
            if(userFull.getFirstNameDat()!=null&&userFull.getLastNameDat()!=null){
                fullName.setDative(userFull.getFirstNameDat()+" "+userFull.getLastNameDat());
            }
            if(userFull.getFirstNameAbl()!=null&&userFull.getLastNameAbl()!=null){
                fullName.setPrepositional(userFull.getFirstNameAbl()+" "+userFull.getLastNameAbl());
            }
            resultToReturn.add(fullName);
        }
        return resultToReturn;
    }


    public List<UserFullNameInEachCase> mapGroupNames(@NonNull List<GroupFull> groupFullList){
        List<UserFullNameInEachCase> resultToReturn = new ArrayList<>();

        for(GroupFull groupFull: groupFullList){
            UserFullNameInEachCase fullName = new UserFullNameInEachCase();
            fullName.setUserId(-groupFull.getId());
            fullName.setNominative(groupFull.getName());
            resultToReturn.add(fullName);
        }
        return resultToReturn;
    }
}


