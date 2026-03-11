package com.example.my_bot.client;

import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.httpclient.HttpTransportClient;
import com.vk.api.sdk.objects.messages.ConversationMember;
import com.vk.api.sdk.objects.messages.responses.GetConversationMembersResponse;
import com.vk.api.sdk.objects.utils.DomainResolvedType;
import com.vk.api.sdk.objects.utils.responses.ResolveScreenNameResponse;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.example.my_bot.utils.VkChatUtils.extractPeerId;

@Component
@Slf4j
public class VkChatClient{
    private final VkApiClient vkApiClient;
    private final GroupActor groupActor;

    public VkChatClient(
            @Value("${vk.group.id}") long groupId,
            @Value("${vk.group.token}") String accessToken) {
        this.vkApiClient = new VkApiClient(new HttpTransportClient());
        this.groupActor =  new GroupActor(groupId, accessToken);
    }

    public void sendText(long chatId, String text, boolean disableMentions) throws ClientException, ApiException {
        vkApiClient.messages()
                .sendDeprecated(groupActor)
                .peerId(extractPeerId(chatId))
                .message(text)
                .disableMentions(disableMentions)
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
    public void changeChatTitle(long chatId, String newTitle) throws ClientException, ApiException {
        vkApiClient.messages().editChat(groupActor)
                .chatId((int)chatId)
                .title(newTitle)
                .execute();
    }

    public Optional<Long> getMemberIdByNickname(@NonNull String nickName){

        ResolveScreenNameResponse response = null;
        try {
            response = vkApiClient.utils()
                    .resolveScreenName(groupActor, nickName)
                    .execute();
        } catch (ApiException| ClientException e) {
           log.warn("Ошибка при попытке получить Id участника по короткому адресу: {}", nickName, e);
           return Optional.empty();
        }
        if (response != null) {
            DomainResolvedType type =response.getType();
            if(type==DomainResolvedType.USER){
                long value = response.getObjectId();
                return Optional.of(value);
            }if(type==DomainResolvedType.GROUP){
                long value = response.getObjectId()*-1L;
                return Optional.of(value);
            }

        }return  Optional.empty();
    }


    public void kickChatMember(int chatId, long memberId) throws ClientException, ApiException {

        vkApiClient.messages().removeChatUser(groupActor)
                .chatId(chatId)
                .memberId(memberId)
                .execute();
    }


}
