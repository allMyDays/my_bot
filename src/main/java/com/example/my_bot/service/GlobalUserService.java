package com.example.my_bot.service;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.config.CaffeineCacheManager;
import com.example.my_bot.dto.user.GlobalUserDetailsDto;
import com.example.my_bot.dto.user.UserFullNameInEachCase;
import com.example.my_bot.entity.GlobalUserEntity;
import com.example.my_bot.enumeration.user.NameCase;
import com.example.my_bot.exception.user.GlobalGlobalUserDoesNotHaveRequiredBoundChatException;
import com.example.my_bot.exception.user.GlobalGlobalUserNotFoundException;
import com.example.my_bot.mapper.GlobalUserMapper;
import com.example.my_bot.repository.GlobalUserRepository;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class GlobalUserService {

    private final GlobalUserRepository globalUserRepository;

    private final VkChatClient vkChatClient;

    private GlobalUserService selfLink;

    private final CaffeineCacheManager cacheManager;

    private final GlobalUserMapper globalUserMapper;

    private final MemberService memberService;

    private static final long AUTO_UPDATE_NAMES_INTERVAL_MINUTES = 60;

    @Autowired
    @Lazy
    public void setSelfLink(GlobalUserService selfLink) {
        this.selfLink = selfLink;
    }



    public GlobalUserDetailsDto getOrCreateUser(long userId){

        GlobalUserDetailsDto globalUserDetailsDto = cacheManager.getUserDetailsCache().get(userId, k->{
            GlobalUserEntity globalUserEntity = globalUserRepository.findById(userId).orElseGet(()->
                    globalUserRepository.save(new GlobalUserEntity(userId))
            );return globalUserMapper.toUserDetailsDto(globalUserEntity);
        });

        if(isItTimeToUpdateUserFullName(globalUserDetailsDto.getLastFullNameUpdate())){
            selfLink.updateUserNameCases(globalUserDetailsDto.getUserId());

        }return globalUserDetailsDto;
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateUserNameCases(@NonNull GlobalUserEntity globalUserEntity){

        UserFullNameInEachCase fullNameDto;
        try {
            fullNameDto = vkChatClient.getAllNameCases(globalUserEntity.getUserId());
        } catch (ClientException | ApiException e) {
            log.error("could not update name for user with id: {}", globalUserEntity.getUserId(),e);
            return;
        }
        globalUserEntity.setFullNameInAbl(fullNameDto.getPrepositional());
        globalUserEntity.setFullNameInDat(fullNameDto.getDative());
        globalUserEntity.setFullNameInAcc(fullNameDto.getAccusative());
        globalUserEntity.setFullNameInNom(fullNameDto.getNominative());
        globalUserEntity.setFullNameInGen(fullNameDto.getGenitive());
        globalUserEntity.setFullNameInIns(fullNameDto.getInstrumental());

        globalUserEntity.setLastFullNameUpdate(Instant.now());
        GlobalUserEntity savedUser = globalUserRepository.save(globalUserEntity);
        invalidateFullNameCacheByUserId(savedUser.getUserId());
        putUserToCache(globalUserEntity);



    }

    public void updateUserNameCases(long userId){
        selfLink.updateUserNameCases(
                globalUserRepository.findById(userId).orElseThrow(()->
                        new GlobalGlobalUserNotFoundException(userId))
        );

    }
    @Transactional
    public void bindChatToUser(long chatId, long userId){
        GlobalUserEntity globalUserEntity = globalUserRepository.findById(userId)
                .orElseThrow(()->new GlobalGlobalUserNotFoundException(userId));

        globalUserEntity.setBoundChat(chatId);
        putUserToCache(globalUserEntity);
    }
    @Transactional
    public void unBindChatFromUser(long chatId, long fromId, long userToUnbind){

        if(fromId!=userToUnbind) {
            memberService.checkMemberInteractionAbility(chatId, fromId, userToUnbind);
        }
        GlobalUserEntity globalUserEntity = globalUserRepository.findById(userToUnbind)
                .orElseThrow(()->new GlobalGlobalUserNotFoundException(userToUnbind));

        if(globalUserEntity.getBoundChat()==null||chatId!= globalUserEntity.getBoundChat()){
            throw new GlobalGlobalUserDoesNotHaveRequiredBoundChatException(userToUnbind);

        }

        globalUserEntity.setBoundChat(null);
        putUserToCache(globalUserEntity);
    }

    public List<Long> findUserIdsByBoundChat(long chatId){
        return globalUserRepository.findUserIdsByBoundChat(chatId);
    }

    public Optional<String> getUserNameInRequiredCase(long userId, @NonNull NameCase requiredNameCase){

        ConcurrentMap<NameCase, String> userCases = cacheManager.getFullNameCache().get(userId,k->{
            Optional<GlobalUserEntity> userEntityOptional = globalUserRepository.findById(userId);
            ConcurrentHashMap<NameCase, String> result = new ConcurrentHashMap<>();
            if(userEntityOptional.isEmpty()){
                return result;
            }
            GlobalUserEntity user = userEntityOptional.get();
            if(isItTimeToUpdateUserFullName(user.getLastFullNameUpdate())){
                selfLink.updateUserNameCases(user);
            }
            for(NameCase currentNc: NameCase.values()){
                String foundCase =  switch (currentNc){
                    case NOMINATIVE -> user.getFullNameInNom();
                    case GENITIVE -> user.getFullNameInGen();
                    case DATIVE -> user.getFullNameInDat();
                    case ACCUSATIVE -> user.getFullNameInAcc();
                    case INSTRUMENTAL -> user.getFullNameInIns();
                    case PREPOSITIONAL -> user.getFullNameInAbl();
                };
                if(foundCase!=null){
                   result.put(currentNc, foundCase);
                }
            } return result;
        });

        return Optional.ofNullable(userCases.get(requiredNameCase));

    }

    private boolean isItTimeToUpdateUserFullName(Instant lastNameUpdate){
        return (lastNameUpdate==null||Duration.between(lastNameUpdate, Instant.now()).toMinutes() >= AUTO_UPDATE_NAMES_INTERVAL_MINUTES);
    }

    private void invalidateFullNameCacheByUserId(long userId){
        cacheManager.getFullNameCache().invalidate(userId);

    }
    private GlobalUserDetailsDto putUserToCache(@NonNull GlobalUserEntity user){

        GlobalUserDetailsDto userDto = globalUserMapper.toUserDetailsDto(user);

        cacheManager.getUserDetailsCache().put(user.getUserId(), userDto);

        return userDto;

    }


}




