package com.example.my_bot.service;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.config.CaffeineCacheManager;
import com.example.my_bot.dto.user.UserDetailsDto;
import com.example.my_bot.dto.user.UserFullNameInEachCase;
import com.example.my_bot.entity.UserEntity;
import com.example.my_bot.enumeration.user.NameCase;
import com.example.my_bot.exception.user.GlobalUserDoesNotHaveRequiredBoundChatException;
import com.example.my_bot.exception.user.GlobalUserNotFoundException;
import com.example.my_bot.mapper.UserMapper;
import com.example.my_bot.repository.UserRepository;
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
public class UserService {

    private final UserRepository userRepository;

    private final VkChatClient vkChatClient;

    private UserService selfLink;

    private final CaffeineCacheManager cacheManager;

    private final UserMapper userMapper;

    private final MemberService memberService;

    private static final long AUTO_UPDATE_NAMES_INTERVAL_MINUTES = 60;

    @Autowired
    @Lazy
    public void setSelfLink(UserService selfLink) {
        this.selfLink = selfLink;
    }



    public UserDetailsDto getOrCreateUser(long userId){

        UserDetailsDto userDetailsDto = cacheManager.getUserDetailsCache().get(userId,k->{
            UserEntity userEntity = userRepository.findById(userId).orElseGet(()->
                    userRepository.save(new UserEntity(userId))
            );return userMapper.toUserDetailsDto(userEntity);
        });

        if(isItTimeToUpdateUserFullName(userDetailsDto.getLastFullNameUpdate())){
            selfLink.updateUserNameCases(userDetailsDto.getUserId());

        }return userDetailsDto;
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateUserNameCases(@NonNull UserEntity userEntity){

        UserFullNameInEachCase fullNameDto;
        try {
            fullNameDto = vkChatClient.getAllNameCases(userEntity.getUserId());
        } catch (ClientException | ApiException e) {
            log.error("could not update name for user with id: {}",userEntity.getUserId(),e);
            return;
        }
        userEntity.setFullNameInAbl(fullNameDto.getPrepositional());
        userEntity.setFullNameInDat(fullNameDto.getDative());
        userEntity.setFullNameInAcc(fullNameDto.getAccusative());
        userEntity.setFullNameInNom(fullNameDto.getNominative());
        userEntity.setFullNameInGen(fullNameDto.getGenitive());
        userEntity.setFullNameInIns(fullNameDto.getInstrumental());

        userEntity.setLastFullNameUpdate(Instant.now());
        UserEntity savedUser = userRepository.save(userEntity);
        invalidateFullNameCacheByUserId(savedUser.getUserId());
        putUserToCache(userEntity);



    }

    public void updateUserNameCases(long userId){
        selfLink.updateUserNameCases(
                userRepository.findById(userId).orElseThrow(()->
                        new GlobalUserNotFoundException(userId))
        );

    }
    @Transactional
    public void bindChatToUser(long chatId, long userId){
        UserEntity userEntity = userRepository.findById(userId)
                .orElseThrow(()->new GlobalUserNotFoundException(userId));

        userEntity.setBoundChat(chatId);
        putUserToCache(userEntity);
    }
    @Transactional
    public void unBindChatFromUser(long chatId, long fromId, long userToUnbind){

        if(fromId!=userToUnbind) {
            memberService.checkMemberInteractionAbility(chatId, fromId, userToUnbind);
        }
        UserEntity userEntity = userRepository.findById(userToUnbind)
                .orElseThrow(()->new GlobalUserNotFoundException(userToUnbind));

        if(userEntity.getBoundChat()==null||chatId!=userEntity.getBoundChat()){
            throw new GlobalUserDoesNotHaveRequiredBoundChatException(userToUnbind);

        }

        userEntity.setBoundChat(null);
        putUserToCache(userEntity);
    }

    public List<Long> findUserIdsByBoundChat(long chatId){
        return userRepository.findUserIdsByBoundChat(chatId);
    }

    public Optional<String> getUserNameInRequiredCase(long userId, @NonNull NameCase requiredNameCase){

        ConcurrentMap<NameCase, String> userCases = cacheManager.getFullNameCache().get(userId,k->{
            Optional<UserEntity> userEntityOptional = userRepository.findById(userId);
            ConcurrentHashMap<NameCase, String> result = new ConcurrentHashMap<>();
            if(userEntityOptional.isEmpty()){
                return result;
            }UserEntity user = userEntityOptional.get();
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
    private UserDetailsDto putUserToCache(@NonNull UserEntity user){

        UserDetailsDto userDto = userMapper.toUserDetailsDto(user);

        cacheManager.getUserDetailsCache().put(user.getUserId(), userDto);

        return userDto;

    }


}




