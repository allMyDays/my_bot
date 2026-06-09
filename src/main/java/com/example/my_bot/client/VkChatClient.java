package com.example.my_bot.client;

import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.user.UserFullNameInEachCase;
import com.example.my_bot.service.MemberService;
import com.example.my_bot.service.MessageLogService;
import com.example.my_bot.vk.VkSendResponse;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.vk.api.sdk.client.AbstractQueryBuilder;
import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ApiExtendedException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.objects.base.BoolInt;
import com.vk.api.sdk.objects.base.responses.BoolResponse;
import com.vk.api.sdk.objects.messages.ConversationMember;
import com.vk.api.sdk.objects.messages.DeleteFullResponseItem;
import com.vk.api.sdk.objects.messages.Forward;
import com.vk.api.sdk.objects.messages.responses.DeleteFullResponse;
import com.vk.api.sdk.objects.messages.responses.GetByConversationMessageIdResponse;
import com.vk.api.sdk.objects.messages.responses.GetConversationMembersResponse;
import com.vk.api.sdk.objects.messages.responses.IsMessagesFromGroupAllowedResponse;
import com.vk.api.sdk.objects.utils.DomainResolvedType;
import com.vk.api.sdk.objects.utils.responses.ResolveScreenNameResponse;
import com.vk.api.sdk.queries.execute.ExecuteBatchQuery;
import com.vk.api.sdk.queries.messages.MessagesDeleteQueryWithFull;
import com.vk.api.sdk.queries.messages.MessagesRemoveChatUserQuery;
import com.vk.api.sdk.queries.messages.MessagesSendQueryWithDeprecated;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;

import static com.example.my_bot.enumeration.member.MemberPresenceType.KICKED;
import static com.example.my_bot.utils.ChatUtils.*;
import static com.example.my_bot.vk.enumeration.CommunityErrorCode.NO_GROUP_MEMBERS_ACCESS;
import static com.vk.api.sdk.objects.users.Fields.*;

@Component
@Slf4j
public class VkChatClient{
    private final VkApiClient vkApiClient;
    private final GroupActor groupActor;
    private final long theBotId;
    private MemberService memberService;
    private MessageLogService messageLogService;

    private static final Gson GSON = new Gson();
    private final RestTemplate restTemplate = new RestTemplate();

    public VkChatClient(VkApiClient vkApiClient, GroupActor groupActor) {
        this.vkApiClient = vkApiClient;
        this.groupActor = groupActor;
        this.theBotId = groupActor.getGroupId();
    }


    @Autowired
    @Lazy
    public void setMemberService(MemberService memberService){
        this.memberService = memberService;
    }

    @Autowired
    @Lazy
    public void setMessageLogService(MessageLogService messageLogService){
        this.messageLogService = messageLogService;
    }

    public void sendText(@NonNull SendMessageDto sendMessageDto) throws ClientException, ApiException{

     if(sendMessageDto.isDoNotSendMessage()) return;
     String text = sendMessageDto.getText();

      while (text.length()>MAX_MESSAGE_LENGTH){
          int cutIndex=text.lastIndexOf(" ", MAX_MESSAGE_LENGTH);
          if(cutIndex==-1){
              cutIndex = MAX_MESSAGE_LENGTH;
          }
          String part = text.substring(0, cutIndex);
          sendMessageDto.setText(part);
          sendNotLongText(sendMessageDto);
          text = text.substring(cutIndex).trim();
      }
      sendMessageDto.setText(text);
      sendNotLongText(sendMessageDto);
  }

    private void sendNotLongText(@NonNull SendMessageDto sendMessageDto) throws ClientException, ApiException{

        long peerId = sendMessageDto.getPeerId();

        MessagesSendQueryWithDeprecated query = vkApiClient.messages()
                .sendDeprecated(groupActor)
                .peerIds(peerId)
                .message(sendMessageDto.getText())
                .disableMentions(!sendMessageDto.isAbleMentions())
                .randomId((int) System.currentTimeMillis());

        if(sendMessageDto.isReplyToMessageId()&&sendMessageDto.getConversationMessageId()!=null){
            Forward forward = new Forward();
            forward.setConversationMessageIds(List.of(sendMessageDto.getConversationMessageId()));
            forward.setPeerId(peerId);
            forward.setIsReply(true);

            query.forward(forward);
        }
        if(sendMessageDto.getForward()!=null){
            query.forward(sendMessageDto.getForward());
        }

        String jsonResponse = query.executeAsString();

        if(!isPersonalChat(peerId)){
            VkSendResponse resp = GSON.fromJson(jsonResponse, VkSendResponse.class);
            if(resp==null||resp.response==null||resp.response.isEmpty()){
                log.warn("cannot get cmid of just sent message cause vk sent not full response {} ",resp);
                return;
            }
            int cmId = resp.response.get(0).conversationMessageId;
            messageLogService.saveNewMessageLog(peerId, -theBotId, cmId, null, sendMessageDto.getText(), sendMessageDto.isForwardedToLogChat());
        }

    }

    public GetConversationMembersResponse getAllConversationMembersWithAllNameCases(long chatId) throws ClientException, ApiException {

        return vkApiClient.messages()
                        .getConversationMembers(groupActor, convertToPeerId(chatId))
                        .fields(
                                FIRST_NAME_NOM, FIRST_NAME_GEN, FIRST_NAME_DAT, FIRST_NAME_ACC, FIRST_NAME_INS, FIRST_NAME_ABL,
                                LAST_NAME_NOM, LAST_NAME_GEN, LAST_NAME_DAT, LAST_NAME_ACC, LAST_NAME_INS, LAST_NAME_ABL
                        )
                        .execute();
    }

    public void changeChatTitle(long chatId, String newTitle) throws ClientException, ApiException {
        vkApiClient.messages().editChat(groupActor)
                .chatId((int)chatId)
                .title(newTitle)
                .execute();
    }

    public Optional<Long> getMemberIdByScreenName(@NonNull String nickName){

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

    public void kickOneChatMember(long chatId, long memberId) throws ClientException, ApiException{

        if(memberId==-theBotId){
            return;
        }
        vkApiClient.messages().removeChatUser(groupActor)
                .chatId((int)chatId)
                .memberId(memberId)
                .execute();

        memberService.setPresenceTypeToMember(chatId, memberId, KICKED, true);
    }

    public Set<Long> kickManyChatMembers(long chatId, @NonNull List<Long> allMemberIds) throws ClientException, ApiException{

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
                if (memberId.equals(-theBotId)) {
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

    public boolean canTheBotWriteToUser(long userId) {
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

    public boolean isCommunityMember(long groupId, long userId) throws ClientException, ApiException {

        if(isGroupId(userId)) return false;
        try{
            BoolResponse response = vkApiClient.groups()
                    .isMember(groupActor)
                    .groupId(String.valueOf(Math.abs(groupId)))
                    .userId(userId)
                    .execute();

            return response!=null&&response.getValue()==1;

        }catch(ApiExtendedException ex){
            int errorCode = ex.getErrorRaw().getErrorCode();
            if(NO_GROUP_MEMBERS_ACCESS.getCodes().contains(errorCode)){
                // доступ к списку участников закрыт или группа заблокирована
                return false;
            }
            return false;
        }
    }

     public void getFullConversationMessage(long chatId, int conversationMessageId) throws ClientException, ApiException {
         GetByConversationMessageIdResponse response = vkApiClient.messages().getByConversationMessageId(groupActor)
                 .peerId(convertToPeerId(chatId))
                 .conversationMessageIds(conversationMessageId)
                 .execute();
     }

    public String changeChatMemberRestrictions(long chatId, long memberId, long seconds, boolean mute){
        String url = "https://api.vk.com/method/messages.changeConversationMemberRestrictions";

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("peer_id", String.valueOf(convertToPeerId(chatId)));
        body.add("member_ids", String.valueOf(memberId));
        if(mute){
            body.add("action", "ro");
            body.add("for", String.valueOf(seconds));
        }else{
            body.add("action", "rw");
        }
        body.add("access_token", groupActor.getAccessToken());
        body.add("v", "5.199");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        HttpEntity<MultiValueMap<String, String>> request =
                new HttpEntity<>(body, headers);

        return restTemplate.postForObject(url, request, String.class);
    }

    public Set<Long> getMembersWithWriteRestriction(long chatId) throws ClientException, ApiException{
        GetConversationMembersResponse response = vkApiClient.messages()
                .getConversationMembers(groupActor, convertToPeerId(chatId))
                .execute();

        return response.getItems().stream()
                .filter(ConversationMember::getIsRestrictedToWrite)
                .map(ConversationMember::getMemberId)
                .collect(Collectors.toSet());
    }

    public Set<Integer> batchDeleteMessages(long chatId, @NonNull List<Integer> messagesToDelete) throws ApiException, ClientException {
        if (messagesToDelete.isEmpty()) return Collections.emptySet();

        List<MessagesDeleteQueryWithFull> deleteQueries = new ArrayList<>();

        List<List<Integer>> cmidBatches = partitionList(messagesToDelete, MAX_CMIDS_IN_ONE_DELETION_METHOD_CALL);

        for(List<Integer> batchCmids : cmidBatches){
            MessagesDeleteQueryWithFull query = vkApiClient.messages()
                    .deleteFull(groupActor)
                    .peerId(convertToPeerId(chatId))
                    .cmids(batchCmids)
                    .deleteForAll(true);
            deleteQueries.add(query);

            if(deleteQueries.size()>=MAX_QUERIES_IN_ONE_BATCH) break;
        }

        ExecuteBatchQuery batchQuery = vkApiClient.execute().batch(groupActor, deleteQueries.toArray(new MessagesDeleteQueryWithFull[0]));

        JsonElement batchResponse = batchQuery.execute();
        log.info("chat {}: batch deletion messages execute result: {}",chatId, batchResponse);

        Type type = new TypeToken<List<List<DeleteFullResponse>>>(){}.getType();
        List<List<DeleteFullResponse>> nested = GSON.fromJson(batchResponse, type);
        List<DeleteFullResponse> flat = nested.stream().flatMap(List::stream).toList();

        Set<Integer> justDeletedByTheBot = flat.stream()
                .filter(DeleteFullResponseItem::isResponse)
                .map(DeleteFullResponseItem::getConversationMessageId)
                .collect(Collectors.toSet());

        messageLogService.markMessagesAsDeleted(chatId, justDeletedByTheBot);
        return justDeletedByTheBot;

    }

    private <T> List<List<T>> partitionList(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }




}
