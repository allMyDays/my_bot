package com.example.my_bot.service;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.dto.user.UserFullNameInEachCase;
import com.example.my_bot.entity.UserEntity;
import com.example.my_bot.enumeration.user.NameCase;
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
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;

    private final VkChatClient vkChatClient;

    private UserService selfLink;

    private static final long AUTO_UPDATE_NAMES_INTERVAL_MINUTES = 60;



    @Autowired
    @Lazy
    public void setSelfLink(UserService selfLink) {
        this.selfLink = selfLink;
    }

    public UserEntity getOrCreateUser(long userId){
        UserEntity userEntity = userRepository.findById(userId).orElseGet(()->
                userRepository.save(new UserEntity(userId))
        );
        Instant lastNameUpdate = userEntity.getLastFullNameUpdate();
        if(lastNameUpdate==null||
                Duration.between(lastNameUpdate, Instant.now()).toMinutes() >= AUTO_UPDATE_NAMES_INTERVAL_MINUTES){
            selfLink.updateUserNameCases(userEntity);

        }return userEntity;
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
        userRepository.save(userEntity);


    }

    public Optional<String> getUserNameInRequiredCase(long userId, @NonNull NameCase nameCase){

        Optional<UserEntity> userEntityOptional = userRepository.findById(userId);
        if(userEntityOptional.isEmpty()){
            return Optional.empty();
        }UserEntity user = userEntityOptional.get();

        String foundCase =  switch (nameCase){
            case NOMINATIVE -> user.getFullNameInNom();
            case GENITIVE -> user.getFullNameInGen();
            case DATIVE -> user.getFullNameInDat();
            case ACCUSATIVE -> user.getFullNameInAcc();
            case INSTRUMENTAL -> user.getFullNameInIns();
            case PREPOSITIONAL -> user.getFullNameInAbl();
        };
        return Optional.ofNullable(foundCase);

    }

}




