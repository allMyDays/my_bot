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




    public void sendText(long peerId, String text) throws ClientException, ApiException {
        vkApiClient.messages()
                .sendDeprecated(groupActor)
                .peerId(peerId)
                .message(text)
                .randomId((int) (System.currentTimeMillis() & 0xFFFFFFFFL))
                .execute();

    }

    public List<ConversationMember> getAllConversationMembers(long chatId) throws ClientException, ApiException {

        GetConversationMembersResponse membersResponse =
                vkApiClient.messages()
                        .getConversationMembers(groupActor, extractPeerId(chatId))
                        .execute();
        return membersResponse.getItems();

    }


}
