package com.example.my_bot.service.chat;

import com.example.my_bot.cache.key.GroupIdAndChatIdKey;
import com.example.my_bot.config.CaffeineCacheManager;
import com.example.my_bot.dto.chat.ChatDetailsDto;
import com.example.my_bot.entity.ChatEntity;
import com.example.my_bot.enumeration.TimeZoneType;
import com.example.my_bot.enumeration.chat.SwitchChatSettingResult;
import com.example.my_bot.exception.chat.LogChatException;
import com.example.my_bot.exception.chat.ChatEntityAlreadyExistsException;
import com.example.my_bot.exception.chat.ChatEntityNotFoundException;
import com.example.my_bot.exception.chat.ForbiddenPrefixException;
import com.example.my_bot.exception.submanager.CannotFindSubmanagerChatIdByMainChatIdException;
import com.example.my_bot.mapper.ChatMapper;
import com.example.my_bot.repository.chat.ChatRepository;
import com.example.my_bot.service.BanService;
import com.example.my_bot.service.MemberService;
import com.example.my_bot.service.WarnService;
import com.example.my_bot.utils.ChatUtils;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
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
    private final ChatService selfLink;
    private final MemberService memberService;

    private final static Set<Character> FORBIDDEN_PREFIXES = Set.of('*','@');

    public ChatService(CaffeineCacheManager cacheManager, ChatRepository chatRepository, ChatMapper chatMapper, @Lazy ChatService selfLink, @Lazy MemberService memberService) {
        this.cacheManager = cacheManager;
        this.chatRepository = chatRepository;
        this.chatMapper = chatMapper;
        this.selfLink = selfLink;
        this.memberService = memberService;
    }

    private Optional<ChatEntity> getChatEntity(long chatId){
        return chatRepository.findById(chatId);
    }

    public ChatEntity findByChatIdOrThrow(long chatId){
        return getChatEntity(chatId).orElseThrow(()->
                new ChatEntityNotFoundException(chatId));

    }

    private ChatDetailsDto putChatToCache(@NonNull ChatEntity chat){
        ChatDetailsDto chatDetails = chatMapper.toChatDetailsDto(chat);

        cacheManager.getChatDetailsCache().asMap().compute(chat.getChatId(),(k,v)-> chatDetails);
        return chatDetails;
    }

    public ChatDetailsDto getCachedChatDetails(long chatId, boolean createIfAbsent){

        return cacheManager.getChatDetailsCache().get(chatId, id -> {
            Optional<ChatEntity> optionalChat = getChatEntity(id);
            if(optionalChat.isPresent()){
                return chatMapper.toChatDetailsDto(optionalChat.get());
            }
            if(createIfAbsent){
                ChatEntity newEntity = selfLink.createNewChatWithStandardSettings(id);
                return chatMapper.toChatDetailsDto(newEntity);
            }else{
                throw new ChatEntityNotFoundException(id);
            }
        });
    }

    public Optional<ChatDetailsDto> getMainChatDataBySubmanagerChatId(long submanagerId, long submanagerChatId){
        GroupIdAndChatIdKey key = new GroupIdAndChatIdKey(submanagerId, submanagerChatId);

        Optional<Long> mainChatId =  cacheManager.getMainChatIdBySubmanagerChatIdCache().get(key, k->
                chatRepository.findMainChatIdBySubmanagerChatId(submanagerId, submanagerChatId)
        );
        return mainChatId.map(id->getCachedChatDetails(id, true));

    }

    public long getSubmanagerChatIdByMainChatId(long submanagerId, long mainChatId){
        GroupIdAndChatIdKey key = new GroupIdAndChatIdKey(submanagerId, mainChatId);

        Optional<Long> submanagerChatId= cacheManager.getSubmanagerChatIdByMainChatIdCache().get(key, k->
                chatRepository.findSubmanagerChatIdByMainChatId(submanagerId, mainChatId)
        );
        return submanagerChatId.orElseThrow(()->{
            log.warn("cannot find submanager chat id by the main chat id {}. Submanager group id: {}", mainChatId, submanagerId);
            return new CannotFindSubmanagerChatIdByMainChatIdException(submanagerId, mainChatId);
        });
    }

    public ChatEntity findByChatCodeOrThrow(@NonNull String code){
        return chatRepository.findByChatCode(code)
                .orElseThrow(()-> new ChatEntityNotFoundException(code));
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
    public void setChatTitle(long chatId, @NonNull String newTitle){

        ChatEntity chat = findByChatIdOrThrow(chatId);
        chat.setChatTitle(newTitle);
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

    @Transactional
    public SwitchChatSettingResult switchSubPosts(long chatId){
        ChatEntity chat = findByChatIdOrThrow(chatId);

        SwitchChatSettingResult resultToReturn;
        if(chat.isSubPosts()){
            chat.setSubPosts(false);
            resultToReturn = SwitchChatSettingResult.OFF;
        }else{
            chat.setSubPosts(true);
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
    public long setDefaultBanPeriod(long chatId, long banTimePeriodSec){
        if(banTimePeriodSec>BanService.MAX_BAN_TIME_PERIOD_SEC){
            banTimePeriodSec = BanService.MAX_BAN_TIME_PERIOD_SEC;
        }
        if(banTimePeriodSec<BanService.MIN_BAN_TIME_PERIOD_SEC){
            banTimePeriodSec = BanService.MIN_BAN_TIME_PERIOD_SEC;
        }
        ChatEntity chat = findByChatIdOrThrow(chatId);
        chat.setBanTimePeriodSec(banTimePeriodSec);
        putChatToCache(chatRepository.save(chat));

        return banTimePeriodSec;
    }

    @Transactional
    public void disableDefaultBanPeriod(long chatId){
        ChatEntity chat = findByChatIdOrThrow(chatId);
        if(chat.getBanTimePeriodSec()!=null){
            chat.setBanTimePeriodSec(null);
            putChatToCache(chatRepository.save(chat));
        }
    }

    public Optional<Long> getDefaultBanTimePeriod(long chatId){
        return getCachedChatDetails(chatId, false).getOptionalBanPeriod();
    }

    public Optional<Long> getDefaultWarnTimePeriod(long chatId){
        return Optional.ofNullable(getCachedChatDetails(chatId, false).getWarnTimePeriodSec());
    }

    public int getWarnMaxQuantity(long chatId){
        return getCachedChatDetails(chatId, false).getWarnMaxQuantity();
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
            chat.setWarnMaxQuantity(WarnService.DEFAULT_CHAT_WARN_QUANTITY);

            try {
                chatRepository.saveAndFlush(chat);
                return chat;
            } catch (DataIntegrityViolationException e){
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

    @Transactional
    public void setLogChat(@NonNull String currentChatCode, long targetChatId, long fromId){

        // !логчат для 6fgf553vd
        //(targetChat)(currentChat)

        ChatEntity currentChat = findByChatCodeOrThrow(currentChatCode);

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
            throw new LogChatException("Ваша роль в указанном чате недостаточно высока для того, чтобы установить логчат для него.");
        }
        if(currentChat.getBoundLogChat()!=null){
            throw new LogChatException("К указанному чату уже привязан логчат. Логчат может быть только один на конкретный чат.");
        }
        if(existsByBoundLogChat(currentChat.getChatId())){
            throw new LogChatException("Указанный вами чат уже является логчатом. Нельзя создать логчат для беседы, которая сама является логчатом.");
        }
        if(!Objects.equals(targetChat.getBoundSubmanagerId(), currentChat.getBoundSubmanagerId())){  // если в обоих чатах 2 основных сообщества, то null.equals(null)
            throw new LogChatException("Логчат может быть установлен только при условии, что в двух чатах работают одинаковые группы (либо субменеджер и тот же субменеджер, либо основное сообщество и основное сообщество).");
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

    public List<ChatEntity> findChatsByBoundSubmanagerAndSubPostsTrue(long submanagerId){
        return chatRepository.findChatsByBoundSubmanagerAndSubPostsTrue(Math.abs(submanagerId));
    }

    @Transactional
    public void setBoundSubmanager(long dataBaseChatId, long submanagerId, long submanagerChatId){
        submanagerId = Math.abs(submanagerId);

        ChatEntity chat= findByChatIdOrThrow(dataBaseChatId);
        chat.setBoundSubmanagerId(Math.abs(submanagerId));
        chat.setSubmanagerChatId(submanagerChatId);
        putChatToCache(chat);

        cacheManager.getMainChatIdBySubmanagerChatIdCache().asMap().compute(
                new GroupIdAndChatIdKey(submanagerId,submanagerChatId), (k,v)-> Optional.of(dataBaseChatId)
        );
        cacheManager.getSubmanagerChatIdByMainChatIdCache().asMap().compute(
                new GroupIdAndChatIdKey(submanagerId, dataBaseChatId), (k,v)-> Optional.of(submanagerChatId)
        );
    }

    @Transactional
    public void setBoundSubmanagerAsNull(long dataBaseChatId, long submanagerId, long submanagerChatId){
        submanagerId = Math.abs(submanagerId);

        ChatEntity chat= findByChatIdOrThrow(dataBaseChatId);
        chat.setBoundSubmanagerId(null);
        chat.setSubmanagerChatId(null);
        putChatToCache(chat);

        cacheManager.getMainChatIdBySubmanagerChatIdCache().asMap().compute(
                new GroupIdAndChatIdKey(submanagerId,submanagerChatId), (k,v)-> Optional.empty()
        );
        cacheManager.getSubmanagerChatIdByMainChatIdCache().asMap().compute(
                new GroupIdAndChatIdKey(submanagerId, dataBaseChatId), (k,v)-> Optional.empty()
        );
    }

}
