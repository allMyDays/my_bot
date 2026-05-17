package com.example.my_bot.service.chat;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.command.commands.LogChatCommand;
import com.example.my_bot.config.CaffeineCacheManager;
import com.example.my_bot.dto.ChatDetailsDto;
import com.example.my_bot.entity.ChatEntity;
import com.example.my_bot.enumeration.DefaultRole;
import com.example.my_bot.enumeration.TimeZoneType;
import com.example.my_bot.enumeration.chat.SwitchChatSettingResult;
import com.example.my_bot.exception.LogChatException;
import com.example.my_bot.exception.chat.ChatEntityAlreadyExistsException;
import com.example.my_bot.exception.chat.ChatEntityNotFoundException;
import com.example.my_bot.exception.chat.ForbiddenPrefixException;
import com.example.my_bot.mapper.ChatMapper;
import com.example.my_bot.repository.ChatRepository;
import com.example.my_bot.service.BanService;
import com.example.my_bot.service.CommandAccessService;
import com.example.my_bot.service.MemberService;
import com.example.my_bot.utils.ChatUtils;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

import static com.example.my_bot.enumeration.DefaultRole.SENIOR_ADMINISTRATOR;
import static com.example.my_bot.utils.ChatUtils.DEFAULT_CHAT_PREFIX;


@Slf4j
@Service
public class ChatService {

    private final CaffeineCacheManager cacheManager;

    private final ChatRepository chatRepository;

    private final ChatMapper chatMapper;

    private final BanService banService;

    private final ChatService selfLink;

    private final MemberService memberService;

    private final static Set<Character> FORBIDDEN_PREFIXES = Set.of('*','@');

    public ChatService(CaffeineCacheManager cacheManager, ChatRepository chatRepository, ChatMapper chatMapper, @Lazy BanService banService,@Lazy ChatService selfLink,@Lazy MemberService memberService) {
        this.cacheManager = cacheManager;
        this.chatRepository = chatRepository;
        this.chatMapper = chatMapper;
        this.banService = banService;
        this.selfLink = selfLink;
        this.memberService = memberService;
    }

    private Optional<ChatEntity> getChatEntity(long chatId){
        return chatRepository.findById(chatId);
    }

    private ChatEntity findByChatIdOrThrow(long chatId){
        return getChatEntity(chatId).orElseThrow(()->
                new ChatEntityNotFoundException(chatId));

    }

    public ChatDetailsDto getCachedChatDetails(long chatId, boolean createIfAbsent){
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

    public Optional<ChatEntity> findByChatCode(@NonNull String code){
        return chatRepository.findByChatCode(code);
    }

    public boolean existsByBoundLogChat(long chatId){
        return chatRepository.existsByBoundLogChat(chatId);
    }

    public List<ChatEntity> findByBoundLogChat(long chatId){
        return chatRepository.findByBoundLogChat(chatId);
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
        putChatToCache(chatRepository.save(chat));
        return resultToReturn;

    }
    @Transactional
    public SwitchChatSettingResult switchMessageReplying(long chatId){

        ChatEntity chat = findByChatIdOrThrow(chatId);

        SwitchChatSettingResult resultToReturn;
        if(chat.isMessageReplying()){
            chat.setMessageReplying(false);
            resultToReturn = SwitchChatSettingResult.OFF;
        }else{
            chat.setMessageReplying(true);
            resultToReturn = SwitchChatSettingResult.ON;
        }
        putChatToCache(chatRepository.save(chat));
        return resultToReturn;
    }

    public boolean isSilentRestriction(long chatId){
        return getCachedChatDetails(chatId, false).isSilentRestriction();
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

    public boolean isAutoUnban(long chatId){
        return getCachedChatDetails(chatId, false).isAutoUnban();
    }
    @Transactional
    public SwitchChatSettingResult switchAutoUnban(long chatId){

        ChatEntity chat = findByChatIdOrThrow(chatId);

        SwitchChatSettingResult resultToReturn;
        if(chat.isAutoUnban()){
            chat.setAutoUnban(false);
            resultToReturn = SwitchChatSettingResult.OFF;
        }else{
            chat.setAutoUnban(true);
            resultToReturn = SwitchChatSettingResult.ON;
        }
        putChatToCache( chatRepository.save(chat));
        return resultToReturn;

    }

    @Transactional
    public void setLastSyncToNow(long chatId){

        ChatEntity chat = findByChatIdOrThrow(chatId);
        chat.setLastSyncTime(Instant.now());

        putChatToCache(chatRepository.save(chat));
    }

    public ChatEntity createNewChatWithStandardSettings(long chatId){

        while(true){
            ChatEntity chat = new ChatEntity();
            chat.setChatId(chatId);
            chat.setPrefix(DEFAULT_CHAT_PREFIX);
            chat.setTimeZoneType(TimeZoneType.GMT_PLUS_3);
            chat.setChatCode(ChatUtils.generateNewChatCode());
            try {
                chatRepository.saveAndFlush(chat);
                return chat;
            }catch (DataIntegrityViolationException e){
                Throwable cause = e.getCause();
                if(cause instanceof ConstraintViolationException ex){
                    String constrainName = ex.getConstraintName();
                    if("uk_chat_chat_code".equals(constrainName)){
                        continue;    //коллизия уникального чат кода
                    }
                    throw new ChatEntityAlreadyExistsException(chatId);
                }
            }
        }
    }
    private ChatDetailsDto putChatToCache( @NonNull ChatEntity chat){

        ChatDetailsDto chatDto = chatMapper.toChatDetailsDto(chat);

        cacheManager.getChatDetailsCache().put(chat.getChatId(), chatDto);

        return chatDto;

    }
    @Transactional
    public void makeLogChat(@NonNull String currentChatCode, long targetChatId, long fromId){

        // !логчат для 6fgf553vd
        //(targetChat)(currentChat)

        ChatEntity currentChat = findByChatCode(currentChatCode)
                .orElseThrow(()->new LogChatException("Не найдено чатов с таким кодом."));
        ChatEntity targetChat = findByChatIdOrThrow(targetChatId);


        if(Objects.equals(currentChat.getChatId(), targetChat.getChatId())){
            throw new LogChatException("Логчат и чат, из которого будут пересылаться сообщения — не могут быть один и тем же чатом.");
        }
        if(targetChat.getBoundLogChat()!=null){
            throw new LogChatException(
                    "Текущий чат является обычным чатом, к которому привязан логчат. Сделать его логчатом нельзя без предварительной отвязки логчата от него."
            );
        }
        if(memberService.getMemberRolePriority(currentChat.getChatId(),fromId)<SENIOR_ADMINISTRATOR.getRolePriority()){
            throw new LogChatException("Ваша роль в указанном чате недостаточно высока.");
        }
        if(currentChat.getBoundLogChat()!=null){
            throw new LogChatException("К указанному чату уже привязан логчат. Логчат может быть только один на конкретный чат.");
        }
        if(existsByBoundLogChat(currentChat.getChatId())){
            throw new LogChatException("Указанный вами чат уже является логчатом. Нельзя создать логчат для беседы, которая уже является логчатом.");
        }
        try{
            memberService.synchronizeChatMembers(currentChat.getChatId());
        }catch(Exception e){
            throw new LogChatException("Не удалось произвести обновление указанного вами чата. Убедитесь, что я там есть и мне там выданы права администратора.");
        }
        currentChat.setBoundLogChat(targetChat.getChatId());
        putChatToCache(currentChat);
    }

    @Transactional
    public void removeLogChat(long chatId){
        List<ChatEntity> boundChats = findByBoundLogChat(chatId);
        boundChats.forEach(c->{
                c.setBoundLogChat(null);
                putChatToCache(c);
        });
    }

    @Transactional
    public void setBoundLogChatAsNull(long chatId){
        ChatEntity chat  = findByChatIdOrThrow(chatId);
        chat.setBoundLogChat(null);
        putChatToCache(chat);
    }










}
