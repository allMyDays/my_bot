package com.example.my_bot.service;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.config.CaffeineCacheManager;
import com.example.my_bot.dto.user.GlobalUserDetailsDto;
import com.example.my_bot.dto.user.UserFullNameInEachCase;
import com.example.my_bot.entity.GlobalUserEntity;
import com.example.my_bot.enumeration.user.NameCase;
import com.example.my_bot.exception.user.GlobalGlobalUserDoesNotHaveRequiredBoundChatException;
import com.example.my_bot.exception.user.GlobalGlobalUserNotFoundException;
import com.example.my_bot.mapper.FullNameMapper;
import com.example.my_bot.mapper.GlobalUserMapper;
import com.example.my_bot.repository.GlobalUserRepository;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.example.my_bot.enumeration.user.NameCase.NOMINATIVE;
import static com.example.my_bot.utils.TextUtils.createMentionBody;

@Service
@RequiredArgsConstructor
@Slf4j
public class GlobalUserService {

    private final GlobalUserRepository globalUserRepository;
    private final CaffeineCacheManager cacheManager;
    private final GlobalUserMapper globalUserMapper;
    private final MemberService memberService;
    private final FullNameMapper fullNameMapper;


    public GlobalUserDetailsDto getOrCreateUser(long userId){
        return cacheManager.getGlobalUserDetailsCache().get(userId, k->{
            GlobalUserEntity globalUserEntity = globalUserRepository.findById(userId).orElseGet(()->
                    globalUserRepository.save(new GlobalUserEntity(userId))
            );return globalUserMapper.toUserDetailsDto(globalUserEntity);
        });
    }

    @Transactional
    public void bindChatToUser(long chatToBind, long userId){
        GlobalUserEntity globalUserEntity = globalUserRepository.findById(userId)
                .orElseThrow(()->new GlobalGlobalUserNotFoundException(userId));

        globalUserEntity.setBoundChat(chatToBind);
        putUserToCache(globalUserEntity);
    }

    @Transactional
    public void unBindChatFromUser(long chatToUnbind, long fromId, long userToUnbind){
        if(fromId!=userToUnbind) {
            memberService.checkMemberInteractionAbility(chatToUnbind, fromId, userToUnbind,true);
        }
        GlobalUserEntity globalUserEntity = globalUserRepository.findById(userToUnbind)
                .orElseThrow(()->new GlobalGlobalUserNotFoundException(userToUnbind));

        if(globalUserEntity.getBoundChat()==null||chatToUnbind!= globalUserEntity.getBoundChat()){
            throw new GlobalGlobalUserDoesNotHaveRequiredBoundChatException(userToUnbind);
        }
        globalUserEntity.setBoundChat(null);
        putUserToCache(globalUserEntity);
    }

    public Set<Long> findUserIdsByBoundChat(long chatId){
        return globalUserRepository.findUserIdsByBoundChat(chatId);
    }

    public String getUserFullNameInRequiredCase(long userToFind, @NonNull NameCase requiredNameCase){
        return getUserFullNamesInRequiredCase(Set.of(userToFind),requiredNameCase).get(userToFind);
    }

    public Map<Long, String> getUserFullNamesInRequiredCase(@NonNull Set<Long> usersToFind, @NonNull NameCase requiredCase){

        Map<Long, String> existingInCache = new HashMap<>();
        Set<Long> missingInCache = new HashSet<>();

        for(long userToFind: usersToFind){
            ConcurrentHashMap<NameCase, String> fullName = cacheManager.getFullNameCache().getIfPresent(userToFind);
            if(fullName==null) missingInCache.add(userToFind);  // поискать в бд
            else{
                String nameCase = fullName.get(requiredCase);  // в бд имени нет если nameCase=null
                if(nameCase==null) nameCase = fullName.get(NOMINATIVE);
                existingInCache.put(userToFind, nameCase!=null?nameCase:createMentionBody(userToFind));
            }
        }
        if(!missingInCache.isEmpty()){
            Map<Long, ConcurrentHashMap<NameCase, String>> loadedFromDb = new HashMap<>();

            for(GlobalUserEntity user: globalUserRepository.findAllById(missingInCache)){
                ConcurrentHashMap<NameCase, String> nameCasesToPut = new ConcurrentHashMap<>();
                for(NameCase nc: NameCase.values()){
                    String gottenName = fullNameMapper.mapNames(user, nc);
                    if(gottenName!=null) nameCasesToPut.put(nc, gottenName);
                }
                loadedFromDb.put(user.getUserId(), nameCasesToPut);
                missingInCache.remove(user.getUserId());
            }
            missingInCache.forEach(userId -> loadedFromDb.computeIfAbsent(userId, k -> new ConcurrentHashMap<>())); // имён этих юзеров нет в бд
            cacheManager.getFullNameCache().putAll(loadedFromDb);

            loadedFromDb.forEach((userId, hashMap) ->{
                        String nameCase = hashMap.get(requiredCase);
                        if(nameCase==null) nameCase = hashMap.get(NOMINATIVE);
                        existingInCache.put(userId, nameCase!=null?nameCase:createMentionBody(userId));
                    }
            );
        }
        return existingInCache;
    }

    @Transactional
    public void putFullNamesToTheDataBase(
            @NonNull List<UserFullNameInEachCase> namesToSave){

        Set<Long> userIds = new HashSet<>();
        namesToSave.forEach(e->{
            userIds.add(e.getUserId());
            invalidateFullNameCacheByUserId(e.getUserId());
          }
        );
        Map<Long, GlobalUserEntity> existingMap =
                globalUserRepository.findAllById(userIds)
                        .stream()
                        .collect(Collectors.toMap(
                                GlobalUserEntity::getUserId,
                                Function.identity()
                        ));

        for(UserFullNameInEachCase fullName: namesToSave){
            GlobalUserEntity entity = existingMap.computeIfAbsent(
                    fullName.getUserId(),
                    id -> {
                        GlobalUserEntity newEntity = new GlobalUserEntity();
                        newEntity.setUserId(id);
                        return newEntity;
                    }
            );
            fullNameMapper.mapNames(entity, fullName);
        }
        globalUserRepository.saveAll(existingMap.values());
    }

    private void invalidateFullNameCacheByUserId(long userId){
        cacheManager.getFullNameCache().invalidate(userId);
    }

    private GlobalUserDetailsDto putUserToCache(@NonNull GlobalUserEntity user){
        GlobalUserDetailsDto userDto = globalUserMapper.toUserDetailsDto(user);
        cacheManager.getGlobalUserDetailsCache().put(user.getUserId(), userDto);
        return userDto;
    }

}




