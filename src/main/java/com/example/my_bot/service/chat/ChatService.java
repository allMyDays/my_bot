package com.example.my_bot.service.chat;

import com.example.my_bot.config.CaffeineCacheManager;
import com.example.my_bot.dto.ChatDetailsDto;
import com.example.my_bot.entity.ChatEntity;
import com.example.my_bot.enumeration.TimeZoneType;
import com.example.my_bot.enumeration.chat.SwitchChatSettingResult;
import com.example.my_bot.exception.chat.ChatEntityAlreadyExistsException;
import com.example.my_bot.exception.chat.ChatEntityNotFoundException;
import com.example.my_bot.exception.chat.ForbiddenPrefixException;
import com.example.my_bot.mapper.ChatMapper;
import com.example.my_bot.repository.ChatRepository;
import com.example.my_bot.service.BanService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static com.example.my_bot.constant.SettingConstant.DEFAULT_CHAT_PREFIX;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final CaffeineCacheManager cacheManager;

    private final ChatRepository chatRepository;

    private final ChatMapper chatMapper;

    private BanService banService;

    private final static Set<Character> FORBIDDEN_PREFIXES = Set.of('*','@');

    private ChatService selfLink;

    @Autowired
    @Lazy
    public void setSelfLink(ChatService selfLink) {
        this.selfLink = selfLink;
    }

    @Autowired
    @Lazy
    public void setBanService(BanService banService) {
        this.banService = banService;
    }

    private Optional<ChatEntity> getChatEntity(long chatId){
        return chatRepository.findById(chatId);
    }

    private ChatEntity findByChatIdOrThrow(long chatId){
        return getChatEntity(chatId).orElseThrow(()->
                new ChatEntityNotFoundException(chatId));

    }

    public ChatDetailsDto getCachedChatDetails(long chatId, boolean createIfAbsent) {
        return cacheManager.getChatDetailsCache().get(chatId, id -> {
            Optional<ChatEntity> optionalChat = getChatEntity(id);
            if (optionalChat.isPresent()) {
                return chatMapper.toChatDetailsDto(optionalChat.get());
            }
            if (createIfAbsent) {
                ChatEntity newEntity = selfLink.createNewChatWithStandardSettings(id);
                return chatMapper.toChatDetailsDto(newEntity);
            } else {
                throw new ChatEntityNotFoundException(id);
            }
        });
    }
    @Transactional
    public void setChatPrefix(long chatId, char newPrefix){

        if(FORBIDDEN_PREFIXES.contains(newPrefix)){
            throw new ForbiddenPrefixException(newPrefix);
        }
        ChatEntity chat = findByChatIdOrThrow(chatId);
        chat.setPrefix(newPrefix);
        putChatToCache(chatRepository.save(chat));
    }
    @Transactional
    public void disableChatPrefix(long chatId){

        ChatEntity chat = findByChatIdOrThrow(chatId);
        chat.setPrefix(null);
        putChatToCache(chatRepository.save(chat));
    }
    @Transactional
    public SwitchChatSettingResult switchSilentRestriction(long chatId){

        ChatEntity chat = findByChatIdOrThrow(chatId);

        SwitchChatSettingResult resultToReturn;
        if(chat.isSilentRestriction()){
            chat.setSilentRestriction(false);
            resultToReturn = SwitchChatSettingResult.OFF;
        }else{
            chat.setSilentRestriction(true);
            resultToReturn = SwitchChatSettingResult.ON;
        }
        putChatToCache( chatRepository.save(chat));
        return resultToReturn;

    }


    public Optional<Character> getChatPrefix(long chatId){
       return getCachedChatDetails(chatId, false).getOptionalPrefix();

    }
    public TimeZoneType getChatTimeZone(long chatId){
        return getCachedChatDetails(chatId, false).getTimeZoneType();

    }

    @Transactional
    public void setChatTimeZone(long chatId, @NonNull TimeZoneType timeZone){
        ChatEntity chat = findByChatIdOrThrow(chatId);
        chat.setTimeZoneType(timeZone);
        putChatToCache(chatRepository.save(chat));
    }

    @Transactional
    public long setDefaultBanPeriod(long chatId, long banPeriodSeconds){
        if(banPeriodSeconds>banService.getMaxBanPeriodInSeconds()){
            banPeriodSeconds = banService.getMaxBanPeriodInSeconds();
        }if(banPeriodSeconds<banService.getMinBanPeriodInSeconds()){
            banPeriodSeconds = banService.getMinBanPeriodInSeconds();
        }

        ChatEntity chat = findByChatIdOrThrow(chatId);
        chat.setBanPeriodSeconds(banPeriodSeconds);
        putChatToCache(chatRepository.save(chat));

        return banPeriodSeconds;
    }
    @Transactional
    public void disableDefaultBanPeriod(long chatId){
        ChatEntity chat = findByChatIdOrThrow(chatId);
        if(chat.getBanPeriodSeconds()!=null){
            chat.setBanPeriodSeconds(null);
            putChatToCache(chatRepository.save(chat));
        }
    }

    public Optional<Long> getDefaultBanPeriod(long chatId){
        return getCachedChatDetails(chatId, false).getOptionalBanPeriod();

    }

    @Transactional
    public void setLastSyncToNow(long chatId){

        ChatEntity chat = findByChatIdOrThrow(chatId);
        chat.setLastSyncTime(Instant.now());

        putChatToCache(chatRepository.save(chat));
    }
    @Transactional
    public ChatEntity createNewChatWithStandardSettings(long chatId){

        if(getChatEntity(chatId).isPresent()){
            throw new ChatEntityAlreadyExistsException(chatId);
        }

        ChatEntity chat = new ChatEntity();
        chat.setChatId(chatId);
        chat.setPrefix(DEFAULT_CHAT_PREFIX);
        chat.setTimeZoneType(TimeZoneType.GMT_PLUS_3);
        return chatRepository.save(chat);

    }

    private ChatDetailsDto putChatToCache( @NonNull ChatEntity chat){

        ChatDetailsDto chatDto = chatMapper.toChatDetailsDto(chat);

        cacheManager.getChatDetailsCache().put(chat.getChatId(), chatDto);

        return chatDto;

    }


}
