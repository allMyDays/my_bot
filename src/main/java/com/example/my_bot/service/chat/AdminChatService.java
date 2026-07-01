package com.example.my_bot.service.chat;

import com.example.my_bot.config.CaffeineCacheManager;
import com.example.my_bot.dto.chat.AdminChatDto;
import com.example.my_bot.entity.AdminChatEntity;
import com.example.my_bot.entity.ChatEntity;
import com.example.my_bot.exception.chat.AdminChatException;
import com.example.my_bot.exception.chat.AdminChatNotFoundException;
import com.example.my_bot.mapper.ChatMapper;
import com.example.my_bot.repository.chat.AdminChatRepository;
import com.example.my_bot.service.MemberService;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

import static com.example.my_bot.enumeration.DefaultRole.SENIOR_ADMINISTRATOR;


@Slf4j
@Service
public class AdminChatService {

    private final CaffeineCacheManager cacheManager;
    private final AdminChatRepository adminChatRepository;
    private final ChatMapper chatMapper;
    private final ChatService chatService;
    private final MemberService memberService;

    private final static int MAX_BOUND_CHATS = 25;


    public AdminChatService(CaffeineCacheManager cacheManager, AdminChatRepository adminChatRepository, ChatMapper chatMapper, ChatService chatService, @Lazy MemberService memberService) {
        this.cacheManager = cacheManager;
        this.adminChatRepository = adminChatRepository;
        this.chatMapper = chatMapper;
        this.chatService = chatService;
        this.memberService = memberService;
    }

    public Optional<AdminChatDto> getAdminChatData(long chatId){

        return cacheManager.getAdminChatCache().get(chatId, key->
                adminChatRepository.findById(key).map(chatMapper::toAdminChatDto)
        );
    }

    @Transactional
    public void unBindChatFromAdminChat(long adminChatId, @NonNull String chatCode){

        long chatId = chatService.findByChatCodeOrThrow(chatCode).getChatId();

        AdminChatEntity adminChat = adminChatRepository.findById(adminChatId)
                .orElseThrow(()->new AdminChatNotFoundException(adminChatId));

        if(!adminChat.getBoundChats().contains(chatId)){
            throw new AdminChatException("К данному админ-чату не привязан указанный вами чат.");
        }
        if(adminChat.getBoundChats().size()==1){
            removeAdminChat(adminChatId);
            return;
        }

        adminChat.getBoundChats().remove(chatId);
        cacheManager.getAdminChatCache().asMap().compute(adminChatId,(k,v)->
                Optional.of(chatMapper.toAdminChatDto(adminChat))
        );
    }

    public void removeAdminChat(long chatId){

        int deletedRaws = adminChatRepository.deleteByChatId(chatId);
        if(deletedRaws==0) throw new AdminChatNotFoundException(chatId);

        cacheManager.getAdminChatCache().asMap().compute(chatId,(k,v)->Optional.empty());
    }

    @Transactional
    public void setAdminChat(@NonNull String currentChatCode, long targetChatId, long fromId){
        // !админчат для 6fgf553vd
        //(targetChat)(currentChat)

        ChatEntity currentChat = chatService.findByChatCodeOrThrow(currentChatCode);

        ChatEntity targetChat = chatService.findByChatIdOrThrow(targetChatId);

        if(Objects.equals(currentChat.getChatId(), targetChat.getChatId())){
            throw new AdminChatException("Текущий чат является тем же самым чатом, к которому относится указанным вами UID беседы.");
        }

        if(memberService.getMemberRolePriority(currentChat.getChatId(), fromId)<SENIOR_ADMINISTRATOR.getRolePriority()){
            throw new AdminChatException("Ваша роль в указанном чате недостаточно высока для того, чтобы установить админ-чат для него.");
        }

        if(getAdminChatData(currentChat.getChatId()).isPresent()){
            throw new AdminChatException("Указанный вами чат уже является админ-чатом. Нельзя создать админ-чат для беседы, которая сама является админ-чатом.");
        }

        AdminChatEntity targetAdminChat =
                adminChatRepository.findById(targetChat.getChatId()).orElse(new AdminChatEntity(targetChat.getChatId()));

        if(targetAdminChat.getBoundChats().contains(currentChat.getChatId())){
            throw new AdminChatException("К текущему админ-чату уже привязан указанный вами чат.");
        }

        if(targetAdminChat.getBoundChats().size()>=MAX_BOUND_CHATS){
            throw new AdminChatException("К текущему админ-чату уже привязано максимально возможное количество чатов.");
        }

        targetAdminChat.getBoundChats().add(currentChat.getChatId());
        adminChatRepository.save(targetAdminChat);

        cacheManager.getAdminChatCache().asMap().compute(targetChatId,(k,v)->
                Optional.of(chatMapper.toAdminChatDto(targetAdminChat))
        );
    }

}
