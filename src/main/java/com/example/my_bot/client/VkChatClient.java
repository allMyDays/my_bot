package com.example.my_bot.client;

import com.example.my_bot.entity.ChatMemberEntity;
import com.example.my_bot.service.ChatMemberService;
import com.example.my_bot.service.ChatService;
import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.objects.messages.ConversationMember;
import com.vk.api.sdk.objects.messages.responses.GetConversationMembersResponse;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.example.my_bot.enumeration.DefaultRole.*;
import static com.example.my_bot.utils.VkChatUtils.extractPeerId;

@Component
@RequiredArgsConstructor
public class VkChatClient{
    private final VkApiClient vkApiClient;
    private final GroupActor groupActor;
    private final ChatService chatService;
    private final ChatMemberService memberService;




    public void sendText(long peerId, String text) throws ClientException, ApiException {
        vkApiClient.messages()
                .sendDeprecated(groupActor)
                .peerId(peerId)
                .message(text)
                .randomId((int) (System.currentTimeMillis() & 0xFFFFFFFFL))
                .execute();

    }

    @Transactional
    public void synchronizeChatMembers(long chatId) throws ClientException, ApiException {

        GetConversationMembersResponse membersResponse =
                vkApiClient.messages()
                        .getConversationMembers(groupActor, extractPeerId(chatId))
                        .execute();

        Map<Long, ChatMemberEntity> existing =
                memberService.findByChatId(chatId).stream()
                        .collect(Collectors.toMap(ChatMemberEntity::getUserId, Function.identity()));

        Set<Long> currentVkIds = new HashSet<>();

        List<ChatMemberEntity> newMembers = new ArrayList<>();

        for (ConversationMember vkMember : membersResponse.getItems()) {

            long id = vkMember.getMemberId();
            currentVkIds.add(id);

            ChatMemberEntity entity = existing.get(id);

            if (entity == null) {
                entity = new ChatMemberEntity();
                entity.setUserId(id);
                entity.setChatId(chatId);
                newMembers.add(entity);
            }

            entity.setInvitedById(vkMember.getInvitedBy());
            entity.setInChat(true);

            if (Boolean.TRUE.equals(vkMember.getIsOwner())) {
                entity.setRolePriority(CHAT_CREATOR.getRolePriority());
                entity.setChatAdmin(true);
            } else if (Boolean.TRUE.equals(vkMember.getIsAdmin())) {
                entity.setRolePriority(SENIOR_ADMINISTRATOR.getRolePriority());
                entity.setChatAdmin(true);
            } else {
                entity.setChatAdmin(false);
            }
        }

        // помечаю вышедших
        for (ChatMemberEntity entity : existing.values()) {
            if (!currentVkIds.contains(entity.getUserId())) {
                entity.setInChat(false);
                entity.setChatAdmin(false);
            }
        }

        if (!newMembers.isEmpty()) {
            memberService.save(newMembers);
        }
    }


}
