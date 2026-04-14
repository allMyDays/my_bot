package com.example.my_bot.client;

import com.example.my_bot.dto.user.UserFullNameInEachCase;
import com.example.my_bot.service.MemberService;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.vk.api.sdk.client.AbstractQueryBuilder;
import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.httpclient.HttpTransportClient;
import com.vk.api.sdk.objects.base.BoolInt;
import com.vk.api.sdk.objects.base.NameCase;
import com.vk.api.sdk.objects.messages.ConversationMember;
import com.vk.api.sdk.objects.messages.responses.GetConversationMembersResponse;
import com.vk.api.sdk.objects.messages.responses.IsMessagesFromGroupAllowedResponse;
import com.vk.api.sdk.objects.utils.DomainResolvedType;
import com.vk.api.sdk.objects.utils.responses.ResolveScreenNameResponse;
import com.vk.api.sdk.queries.execute.ExecuteBatchQuery;
import com.vk.api.sdk.queries.messages.MessagesRemoveChatUserQuery;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.example.my_bot.enumeration.member.MemberPresenceType.KICKED;
import static com.example.my_bot.utils.ChatUtils.convertToPeerId;

@Component
@Slf4j
public class VkChatClient{
    private final VkApiClient vkApiClient;
    private final GroupActor groupActor;
    private final long groupId;
    private MemberService memberService;

    public VkChatClient(
            @Value("${vk.group.id}") long groupId,
            @Value("${vk.group.token}") String accessToken){
        this.vkApiClient = new VkApiClient(new HttpTransportClient());
        this.groupActor =  new GroupActor(groupId, accessToken);
        this.groupId = groupId;
    }
    @Autowired
    @Lazy
    public void setMemberService(MemberService memberService) {
        this.memberService = memberService;
    }

    public void sendText(String text, long peerId, boolean disableMentions) throws ClientException, ApiException {
        vkApiClient.messages()
                .sendDeprecated(groupActor)
                .peerId(peerId)
                .message(text)
                .disableMentions(disableMentions)
                .randomId((int) (System.currentTimeMillis() & 0xFFFFFFFFL))
                .execute();

    }

    public List<ConversationMember> getAllConversationMembers(long chatId) throws ClientException, ApiException {

        GetConversationMembersResponse membersResponse =
                vkApiClient.messages()
                        .getConversationMembers(groupActor, convertToPeerId(chatId))
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


    public void kickOneChatMember(long chatId, long memberId) throws ClientException, ApiException {

        if (memberId==-groupId){
            return;
        }

        vkApiClient.messages().removeChatUser(groupActor)
                .chatId((int)chatId)
                .memberId(memberId)
                .execute();

        memberService.setPresenceTypeToMember(chatId, memberId, KICKED, true);

    }

    public Set<Long> kickManyChatMembers(long chatId, @NonNull List<Long> allMemberIds) throws ClientException, ApiException {

        final int maxBatchSize = 25;
        List<AbstractQueryBuilder> batchQueries = new ArrayList<>();
        List<Long> batchMemberIds = new ArrayList<>();
        HashSet<Long> kickedMembers = new HashSet<>();

        for (int i = 0; i < allMemberIds.size(); i += maxBatchSize) {
            List<Long> batch = allMemberIds.subList(i, Math.min(i + maxBatchSize, allMemberIds.size()));

            for (Long memberId : batch) {
                if (memberId == null) {
                    log.error("chat {} error: memberId is null in method kickManyChatMembers", chatId);
                    continue;
                }
                if (memberId.equals(-groupId)) {
                    continue;
                }
                MessagesRemoveChatUserQuery removeQuery = vkApiClient.messages()
                        .removeChatUser(groupActor, (int) chatId)
                        .memberId(memberId);
                batchQueries.add(removeQuery);
                batchMemberIds.add(memberId);
            }

            if (!batchQueries.isEmpty()) {
                JsonElement batchResponse = vkApiClient.execute()
                        .batch(groupActor, batchQueries)
                        .execute();

                JsonArray results = batchResponse.getAsJsonArray();
                for (int j = 0; j < results.size(); j++) {
                    JsonElement res = results.get(j);
                    JsonPrimitive prim =res.getAsJsonPrimitive();
                    if ((prim.isNumber() && prim.getAsInt() == 1) || (prim.isBoolean() && prim.getAsBoolean())){
                        kickedMembers.add(batchMemberIds.get(j));
                    } else {
                        log.error("chat {} error: could not kick member {} in method kickManyChatMembers: {}",
                                chatId, batchMemberIds.get(j), res);
                    }
                }

                batchQueries.clear();
                batchMemberIds.clear();
                try {
                    Thread.sleep(500); // пауза между пакетами
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        memberService.setPresenceTypeToMembers(chatId, kickedMembers,KICKED, true);

        return kickedMembers;
    }


    public UserFullNameInEachCase getAllNameCases(long userId) throws ClientException, ApiException {
        String stringUserId = String.valueOf(userId);
        ExecuteBatchQuery batch = vkApiClient.execute().batch(groupActor,
                vkApiClient.users().get(groupActor).userIds(stringUserId).nameCase(NameCase.ACCUSATIVE),
                vkApiClient.users().get(groupActor).userIds(stringUserId).nameCase(NameCase.DATIVE),
                vkApiClient.users().get(groupActor).userIds(stringUserId).nameCase(NameCase.GENITIVE),
                vkApiClient.users().get(groupActor).userIds(stringUserId).nameCase(NameCase.INSTRUMENTAL),
                vkApiClient.users().get(groupActor).userIds(stringUserId).nameCase(NameCase.NOMINATIVE),
                vkApiClient.users().get(groupActor).userIds(stringUserId).nameCase(NameCase.PREPOSITIONAL)
        );
        JsonElement response = batch.execute();
        JsonArray jsonArray = response.getAsJsonArray();

        UserFullNameInEachCase result = new UserFullNameInEachCase();

        for (int i = 0; i < jsonArray.size(); i++) {
            JsonElement element = jsonArray.get(i);
            if (!element.isJsonArray()) continue;

            JsonArray usersArray = element.getAsJsonArray();
            if (usersArray.isEmpty()) continue;

            JsonObject user = usersArray.get(0).getAsJsonObject();
            String firstName = user.get("first_name").getAsString();
            String lastName = user.get("last_name").getAsString();
            String fullName = firstName + " " + lastName;

            switch (i) {
                case 0: result.setAccusative(fullName); break;
                case 1: result.setDative(fullName); break;
                case 2: result.setGenitive(fullName); break;
                case 3: result.setInstrumental(fullName); break;
                case 4: result.setNominative(fullName); break;
                case 5: result.setPrepositional(fullName); break;
            }
        }
        return result;
    }
    public boolean canGroupWriteToUser(long userId) {
        try {
            IsMessagesFromGroupAllowedResponse response = vkApiClient.messages()
                    .isMessagesFromGroupAllowed(groupActor)
                    .groupId(groupActor.getGroupId())
                    .userId(userId)
                    .execute();

            // is_allowed == true → писать можно, false → нельзя
            BoolInt boolInt =  response.getIsAllowed();
            return boolInt.getValue()==1;
        } catch (ApiException | ClientException e) {
            log.error("Ошибка при проверке разрешения для user {}: {}", userId, e.getMessage());
            return false;
        }
    }

}
