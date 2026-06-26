package com.example.my_bot.service.submanager;

import com.example.my_bot.cache.value.callback.GroupIdAndChatId;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.config.CaffeineCacheManager;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.submanager.SubmanagerDto;
import com.example.my_bot.entity.ChatEntity;
import com.example.my_bot.exception.submanager.CannotFindSubmanagerChatIdByMainChatIdException;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.mapper.SubmanagerMapper;
import com.example.my_bot.repository.SubmanagerRepository;
import com.example.my_bot.service.CryptoService;
import com.example.my_bot.service.chat.ChatService;
import com.example.my_bot.utils.ChatUtils;
import com.example.my_bot.utils.SubmanagerUtils;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.example.my_bot.utils.ChatUtils.convertToPeerId;
import static com.example.my_bot.utils.SubmanagerUtils.DEFAULT_SUB_POST_TEXT;
import static com.example.my_bot.utils.TextUtils.createMention;


@Slf4j
@Service
public class SubmanagerActionService {

    private final CryptoService cryptoService;
    private final SubmanagerRepository submanagerRepository;
    private final CaffeineCacheManager cacheManager;
    private final SubmanagerMapper submanagerMapper;
    private final ChatService chatService;
    private final VkChatClient vkChatClient;
    private final MessageMapper messageMapper;

    private final long theMainBotId;
    private final GroupActor theMainBotGroupActor;

    public SubmanagerActionService(CryptoService cryptoService,
                                   SubmanagerRepository submanagerRepository,
                                   CaffeineCacheManager cacheManager,
                                   SubmanagerMapper submanagerMapper,
                                   ChatService chatService,
                                   @Lazy VkChatClient vkChatClient,
                                   MessageMapper messageMapper,
                                   @Value("${vk.main-bot.id}") long theMainBotId,
                                   @Qualifier("theMainBotGroupActor") GroupActor theMainBotGroupActor){

        this.cryptoService = cryptoService;
        this.submanagerRepository = submanagerRepository;
        this.cacheManager = cacheManager;
        this.submanagerMapper = submanagerMapper;
        this.chatService = chatService;
        this.vkChatClient = vkChatClient;
        this.messageMapper = messageMapper;
        this.theMainBotId = theMainBotId;
        this.theMainBotGroupActor = theMainBotGroupActor;
    }

    public boolean tryHandleSubmanagerBinding(long executorBotId, long fromId, long submanagerChatId, @NonNull SubmanagerDto subInfo, @Nullable String bindingCode){

        bindingCode = bindingCode==null?null:bindingCode.trim();

        if(fromId!=-theMainBotId||bindingCode==null||!SubmanagerUtils.stringMatchesABindingCode(bindingCode)){
            return false;
        }
        // чат-менеджер отправил код для привязки субменеджера
        GroupIdAndChatId bindingData = cacheManager.getBindingSubmanagerDataCache().getIfPresent(bindingCode);

        if(bindingData==null||bindingData.groupId()!=executorBotId){
            return false;
        }
        long dataBaseChatId = bindingData.chatId();
        chatService.setBoundSubmanager(dataBaseChatId, executorBotId, submanagerChatId);

        try{
            vkChatClient.sendText(messageMapper.toSendMessageDto(
                    "✅Отлично! Теперь я буду работать в этой беседе вместо %s(Чат-менеджера).".formatted(createMention(-theMainBotId)),
                    convertToPeerId(submanagerChatId),
                    dataBaseChatId,
                    subInfo.getGroupActor()
            ));
            vkChatClient.selfLeave(dataBaseChatId, dataBaseChatId, theMainBotGroupActor);
            vkChatClient.kickOneChatMember(dataBaseChatId, submanagerChatId, subInfo.getGroupActor(), -theMainBotId);
        }
        catch (ApiException | ClientException e){
            log.warn("chat {}: error while executing vk actions after submanager {} has been successfully bound to the chat",dataBaseChatId, executorBotId, e);
        }
        return true;
    }

    public void handleSubmanagerUnBinding(long dataBaseChatId, @NonNull GroupActor subToUnbind, long submanagerChatId){

        chatService.setBoundSubmanagerAsNull(dataBaseChatId, subToUnbind.getGroupId(), submanagerChatId);

        try{
            vkChatClient.sendText(messageMapper.toSendMessageDto(
                    "Я покидаю вас. Управление чатом возвращается к %s(Чат-менеджеру).".formatted(createMention(-theMainBotId)),
                    convertToPeerId(submanagerChatId),
                    dataBaseChatId,
                    subToUnbind
            ));
            vkChatClient.selfLeave(dataBaseChatId, submanagerChatId, subToUnbind);
            vkChatClient.kickOneChatMember(dataBaseChatId, dataBaseChatId, theMainBotGroupActor, -subToUnbind.getGroupId());
        }
        catch (ApiException | ClientException e){
            log.warn("chat {}: error while executing vk actions after submanager {} has been successfully unbound from the chat",dataBaseChatId, subToUnbind.getGroupId(), e);
        }
    }

    public void sendNewSubPostToRequiredChats(@NonNull SubmanagerDto subInfo, int postId, long fromId){

        if(fromId!=-subInfo.getGroupId()) return;

        List<ChatEntity> requiredChats = chatService.findChatsByBoundSubmanagerAndSubPostsTrue(subInfo.getGroupId());

        for(ChatEntity chat: requiredChats){
            long submanagerChatId;
            try{
                submanagerChatId= chatService.getSubmanagerChatIdByMainChatId(subInfo.getGroupId(), chat.getChatId());
            }catch (CannotFindSubmanagerChatIdByMainChatIdException e){
                continue;
            }
            SendMessageDto sendMessage = messageMapper.toSendMessageDto(DEFAULT_SUB_POST_TEXT, convertToPeerId(submanagerChatId), chat.getChatId(), subInfo.getGroupActor());
            sendMessage.setAttachment(ChatUtils.buildGroupWallPostAsAttachment(subInfo.getGroupId(), postId));

            try {
                vkChatClient.sendText(sendMessage);
            } catch (ClientException | ApiException e){
                log.info("chat {}: error trying send new post from submanager group {}",chat.getChatId(), subInfo.getGroupId(), e);
            }
        }
    }
















}
