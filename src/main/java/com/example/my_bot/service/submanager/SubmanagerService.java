package com.example.my_bot.service.submanager;

import com.example.my_bot.config.CaffeineCacheManager;
import com.example.my_bot.dto.submanager.SubmanagerDto;
import com.example.my_bot.entity.SubmanagerEntity;
import com.example.my_bot.exception.submanager.SubmanagerNotFoundException;
import com.example.my_bot.mapper.SubmanagerMapper;
import com.example.my_bot.repository.SubmanagerRepository;
import com.example.my_bot.service.CryptoService;
import com.vk.api.sdk.client.actors.GroupActor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;


@Slf4j
@Service
public class SubmanagerService {

    private final CryptoService cryptoService;
    private final SubmanagerRepository submanagerRepository;
    private final CaffeineCacheManager cacheManager;
    private final SubmanagerMapper submanagerMapper;

    private final long theMainBotId;
    private final GroupActor theMainBotGroupActor;

    public SubmanagerService(CryptoService cryptoService,
                             SubmanagerRepository submanagerRepository,
                             CaffeineCacheManager cacheManager,
                             SubmanagerMapper submanagerMapper,
                             @Value("${vk.main-bot.id}") long theMainBotId,
                             @Qualifier("theMainBotGroupActor") GroupActor theMainBotGroupActor){

        this.cryptoService = cryptoService;
        this.submanagerRepository = submanagerRepository;
        this.cacheManager = cacheManager;
        this.submanagerMapper = submanagerMapper;
        this.theMainBotId = theMainBotId;
        this.theMainBotGroupActor = theMainBotGroupActor;
    }

    @Transactional
    public void createOrUpdateSubmanagerInfo(long groupId, @NonNull String groupToken, int serverId, @NonNull String secretKey){

        groupId = Math.abs(groupId);
        if(groupId==theMainBotId) return;

        SubmanagerEntity savedEntity = submanagerRepository.save(
                new SubmanagerEntity(groupId, cryptoService.encrypt(groupToken),serverId, secretKey)
        );
        cacheManager.getSubmanagerInfoCache().asMap().compute(groupId, (k, v)->
                Optional.of(submanagerMapper.toSubmanagerDto(savedEntity))
        );
    }

    public SubmanagerDto getSubmanagerOrThrowIfAbsents(long groupId){

        return getOptionalSubmanager(groupId).orElseThrow(()->{
            log.warn("cannot find submanager data by group id {}", groupId);
            return new SubmanagerNotFoundException(groupId);
        });
    }

    public Optional<SubmanagerDto> getOptionalSubmanager(long groupId){

        groupId = Math.abs(groupId);
        return cacheManager.getSubmanagerInfoCache().get(groupId, key->{
                    Optional<SubmanagerEntity> entity = submanagerRepository.findById(key);
                    return entity.map(submanagerMapper::toSubmanagerDto);
                }
        );
    }

    public boolean isSubmanager(@NonNull GroupActor groupActor){
        return !Objects.equals(groupActor.getGroupId(), theMainBotGroupActor.getGroupId())
                && !Objects.equals(groupActor.getAccessToken(), theMainBotGroupActor.getAccessToken());
    }

}
