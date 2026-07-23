package com.example.my_bot.client;

import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandRoutingData;
import com.example.my_bot.service.MemberService;
import com.example.my_bot.service.MessageLogService;
import com.example.my_bot.vk.mapping.message.VkSendResponse;
import com.example.my_bot.vk.enumeration.WriteRestrictionAction;
import com.example.my_bot.vk.CustomVkApiClient;
import com.example.my_bot.vk.mapping.restriction.MessageChangeChatMemberRestrictionQuery;
import com.example.my_bot.vk.mapping.restriction.ChangeChatMemberRestrictionResponse;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.vk.api.sdk.client.AbstractQueryBuilder;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.objects.messages.ConversationMember;
import com.vk.api.sdk.objects.messages.DeleteFullResponseItem;
import com.vk.api.sdk.objects.messages.Forward;
import com.vk.api.sdk.objects.messages.responses.DeleteFullResponse;
import com.vk.api.sdk.objects.messages.responses.GetByConversationMessageIdResponse;
import com.vk.api.sdk.objects.messages.responses.GetConversationMembersResponse;
import com.vk.api.sdk.objects.messages.responses.GetConversationsByIdResponse;
import com.vk.api.sdk.objects.utils.DomainResolvedType;
import com.vk.api.sdk.objects.utils.responses.ResolveScreenNameResponse;
import com.vk.api.sdk.queries.execute.ExecuteBatchQuery;
import com.vk.api.sdk.queries.messages.MessagesDeleteQueryWithFull;
import com.vk.api.sdk.queries.messages.MessagesRemoveChatUserQuery;
import com.vk.api.sdk.queries.messages.MessagesSendQueryWithUserIds;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;

import static com.example.my_bot.enumeration.member.MemberPresenceType.KICKED;
import static com.example.my_bot.enumeration.member.MemberPresenceType.SELF_LEAVE;
import static com.example.my_bot.utils.ChatUtils.*;
import static com.vk.api.sdk.objects.users.Fields.*;

@Component
@Slf4j
public class VkChatClient{
    private final CustomVkApiClient vkApiClient;
    private final GroupActor theMainBotGroupActor;
    private final long theMainBotId;
    private final MemberService memberService;
    private final MessageLogService messageLogService;

    private static final Gson GSON = new Gson();

    public VkChatClient(CustomVkApiClient vkApiClient, @Qualifier("theMainBotGroupActor") GroupActor theMainBotGroupActor, @Lazy MemberService memberService, @Lazy MessageLogService messageLogService) {
        this.vkApiClient = vkApiClient;
        this.theMainBotGroupActor = theMainBotGroupActor;
        this.theMainBotId = theMainBotGroupActor.getGroupId();
        this.memberService = memberService;
        this.messageLogService = messageLogService;
    }

   // возвращает cmid отправленных сообщений
    public List<Integer> sendText(@NonNull SendMessageDto sendMessageDto) throws ClientException, ApiException{

     if(sendMessageDto.isDoNotSendTheMessage()) return Collections.emptyList();
     String text = sendMessageDto.getText();

     Set<Integer> sentMessages = new HashSet<>();

      while (text.length()>MAX_MESSAGE_LENGTH){
          int cutIndex=text.lastIndexOf(" ", MAX_MESSAGE_LENGTH);
          if(cutIndex==-1){
              cutIndex = MAX_MESSAGE_LENGTH;
          }
          String part = text.substring(0, cutIndex);
          sendMessageDto.setText(part);
          sentMessages.add(sendTextWithLimitedLength(sendMessageDto));
          text = text.substring(cutIndex).trim();
      }
      sendMessageDto.setText(text);
      sentMessages.add(sendTextWithLimitedLength(sendMessageDto));

      return sentMessages.stream()
              .filter(Objects::nonNull)
              .toList();
  }

    private Integer sendTextWithLimitedLength(@NonNull SendMessageDto sendMessage) throws ClientException {

        long responsePeerId = sendMessage.getResponsePeerId();

        MessagesSendQueryWithUserIds query = vkApiClient.messages()
                .sendUserIds(sendMessage.getResponderBot())
                .peerIds(responsePeerId)
                .message(sendMessage.getText())
                .disableMentions(!sendMessage.isAbleMentions())
                .randomId((int) System.currentTimeMillis());

        if(sendMessage.isReplyToMessageId()&&sendMessage.getConversationMessageId()!=null&&!isPersonalChat(responsePeerId)){
            Forward forward = new Forward();
            forward.setConversationMessageIds(List.of(sendMessage.getConversationMessageId()));
            forward.setPeerId(responsePeerId);
            forward.setIsReply(true);
            query.forward(forward);
        }
        if(sendMessage.getForward()!=null){
            query.forward(sendMessage.getForward());
        }
        if(sendMessage.getAttachment()!=null){
            query.attachment(sendMessage.getAttachment());
        }
        if(sendMessage.getKeyboard()!=null){
            query.keyboard(sendMessage.getKeyboard());
        }
        String jsonResponse = query.executeAsString();
        VkSendResponse resp = GSON.fromJson(jsonResponse, VkSendResponse.class);
        if(resp==null||resp.response==null||resp.response.isEmpty()){
            log.warn("cannot get cmid of just sent message cause vk sent not full response {} ",resp);
            return null;
        }
        int cmid = resp.response.get(0).conversationMessageId;

        if(!isPersonalChat(responsePeerId)){
            messageLogService.saveNewMessageLog(sendMessage.getDataBaseChatId(), -sendMessage.getResponderBot().getGroupId(), cmid, null, sendMessage.getText(), sendMessage.isLogChatForward());
        }
        return cmid;
    }

    public GetConversationMembersResponse getAllConversationMembersWithAllNameCases(@NonNull GroupActor executorBot, long vkApiChatId) throws ClientException, ApiException {

        return vkApiClient.messages()
                        .getConversationMembers(executorBot, convertToPeerId(vkApiChatId))
                        .fields(
                                FIRST_NAME_NOM, FIRST_NAME_GEN, FIRST_NAME_DAT, FIRST_NAME_ACC, FIRST_NAME_INS, FIRST_NAME_ABL,
                                LAST_NAME_NOM, LAST_NAME_GEN, LAST_NAME_DAT, LAST_NAME_ACC, LAST_NAME_INS, LAST_NAME_ABL
                        )
                        .execute();
    }

    public void changeChatTitle(@NonNull GroupActor executorBot, long vkApiChatId, String newTitle) throws ClientException, ApiException {
        vkApiClient.messages().editChat(executorBot)
                .chatId((int)vkApiChatId)
                .title(newTitle)
                .execute();
    }

    public Optional<Long> getMemberIdByScreenName(@NonNull String screenName){

        ResolveScreenNameResponse response = null;
        try {
            response = vkApiClient.utils()
                    .resolveScreenName(theMainBotGroupActor, screenName)
                    .execute();
        } catch (ApiException| ClientException e) {
           log.warn("Ошибка при попытке получить Id участника по короткому адресу: {}", screenName, e);
           return Optional.empty();
        }
        if (response != null) {
            DomainResolvedType type =response.getType();
            if(type==DomainResolvedType.USER){
                long value = response.getObjectId();
                return Optional.of(value);
            }
            if(type==DomainResolvedType.GROUP){
                long value = response.getObjectId()*-1L;
                return Optional.of(value);
            }
        }
        return  Optional.empty();
    }

    public void kickOneChatMember(@NonNull CommandRoutingData commandRoutingData, long memberId) throws ClientException, ApiException{

        long dataBaseChatId = commandRoutingData.getDataBaseChatId();
        long vkApiChatId = commandRoutingData.getVkApiChatId();

        if(memberId==-commandRoutingData.getExecutorBot().getGroupId()){
            return;
        }
        vkApiClient.messages().removeChatUser(commandRoutingData.getExecutorBot())
                .chatId((int)vkApiChatId)
                .memberId(memberId)
                .execute();

        memberService.setPresenceTypeToMember(dataBaseChatId, memberId, KICKED, true);
    }

    public void kickOneChatMember(long databaseChatId, long vkApiChatId, @NonNull GroupActor executorBot, long memberId) throws ClientException, ApiException{

        if(memberId==-executorBot.getGroupId()){
            return;
        }
        vkApiClient.messages().removeChatUser(executorBot)
                .chatId((int)vkApiChatId)
                .memberId(memberId)
                .execute();

        memberService.setPresenceTypeToMember(databaseChatId, memberId, KICKED, true);
    }

    public void selfLeave(long databaseChatId, long vkApiChatId, @NonNull GroupActor executorBot) throws ClientException, ApiException{

        vkApiClient.messages().removeChatUser(executorBot)
                .chatId((int)vkApiChatId)
                .memberId(-executorBot.getGroupId())
                .execute();

        memberService.setPresenceTypeToMember(databaseChatId, -executorBot.getGroupId(), SELF_LEAVE, true);
    }

    public Set<Long> kickManyChatMembers(@NonNull CommandRoutingData commandRoutingData, @NonNull List<Long> allMemberIds) throws ClientException, ApiException{

        GroupActor executorBot = commandRoutingData.getExecutorBot();
        long dataBaseChatId = commandRoutingData.getDataBaseChatId();
        long vkApiChatId = commandRoutingData.getVkApiChatId();

        final int maxBatchSize = 25;
        List<AbstractQueryBuilder> batchQueries = new ArrayList<>();
        List<Long> batchMemberIds = new ArrayList<>();
        HashSet<Long> kickedMembers = new HashSet<>();

        for (int i = 0; i < allMemberIds.size(); i += maxBatchSize) {
            List<Long> batch = allMemberIds.subList(i, Math.min(i + maxBatchSize, allMemberIds.size()));

            for (Long memberId : batch) {
                if (memberId == null) {
                    log.error("chat {} error: memberId is null in method kickManyChatMembers", dataBaseChatId);
                    continue;
                }
                if(memberId.equals(-executorBot.getGroupId())){
                    continue;
                }
                MessagesRemoveChatUserQuery removeQuery = vkApiClient.messages()
                        .removeChatUser(executorBot, (int) vkApiChatId)
                        .memberId(memberId);
                batchQueries.add(removeQuery);
                batchMemberIds.add(memberId);
            }

            if (!batchQueries.isEmpty()) {
                JsonElement batchResponse = vkApiClient.execute()
                        .batch(executorBot, batchQueries)
                        .execute();

                JsonArray results = batchResponse.getAsJsonArray();
                for (int j = 0; j < results.size(); j++) {
                    JsonElement res = results.get(j);
                    JsonPrimitive prim =res.getAsJsonPrimitive();
                    if ((prim.isNumber() && prim.getAsInt() == 1) || (prim.isBoolean() && prim.getAsBoolean())){
                        kickedMembers.add(batchMemberIds.get(j));
                    } else {
                        log.error("chat {} error: could not kick member {} in method kickManyChatMembers: {}",
                                dataBaseChatId, batchMemberIds.get(j), res);
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
        memberService.setPresenceTypeToMembers(dataBaseChatId, kickedMembers,KICKED, true);

        return kickedMembers;
    }

     public void getFullConversationMessage(long chatId, int conversationMessageId) throws ClientException, ApiException {
         GetByConversationMessageIdResponse response = vkApiClient.messages().getByConversationMessageId(theMainBotGroupActor)
                 .peerId(convertToPeerId(chatId))
                 .conversationMessageIds(conversationMessageId)
                 .execute();
     }

    public boolean changeChatMemberRestrictions(@NonNull GroupActor executorBot, long chatId, long memberId, @Nullable Integer timePeriodSec, boolean mute) throws ClientException, ApiException {

        if(memberId==-executorBot.getGroupId()){
            return false;
        }
        MessageChangeChatMemberRestrictionQuery query = vkApiClient.messages().changeChatMemberRestrictions(executorBot)
                .peerId(convertToPeerId(chatId))
                .memberIds(List.of(memberId));
        if(mute){
            query.action(WriteRestrictionAction.READ_ONLY);
            if(timePeriodSec!=null) query.timePeriodSec(timePeriodSec);
        }else{
            query.action(WriteRestrictionAction.READ_AND_WRITE);
        }
        ChangeChatMemberRestrictionResponse response = query.execute();

        return !response.getFailedMemberIds().contains(memberId);
    }

    public Set<Long> getMembersWithWriteRestriction(@NonNull GroupActor executorBot, long chatId) throws ClientException, ApiException{
        GetConversationMembersResponse response = vkApiClient.messages()
                .getConversationMembers(executorBot, convertToPeerId(chatId))
                .execute();

        return response.getItems().stream()
                .filter(ConversationMember::getIsRestrictedToWrite)
                .map(ConversationMember::getMemberId)
                .collect(Collectors.toSet());
    }

    public boolean deleteOneMessage(@NonNull CommandRoutingData commandRoutingData, int conversationMessageId) throws ClientException, ApiException {
        List<DeleteFullResponse> response = vkApiClient.messages()
                .deleteFull(commandRoutingData.getExecutorBot())
                .peerId(convertToPeerId(commandRoutingData.getVkApiChatId()))
                .cmids(conversationMessageId)
                .deleteForAll(true)
                .execute();

        if(response==null){
            log.warn("deleteFull method returned null List<DeleteFullResponse>");
            return false;
        }
        Optional<Integer> cimd = response.stream().filter(DeleteFullResponseItem::isResponse)
                .map(DeleteFullResponseItem::getConversationMessageId)
                .findFirst();

        cimd.ifPresent(id-> messageLogService.markMessagesAsDeleted(commandRoutingData.getDataBaseChatId(), Set.of(id)));
        return cimd.isPresent();
    }

    public boolean deleteOneMessageInTheMainBotPrivateMessages(long userId, int conversationMessageId) throws ClientException, ApiException {
        List<DeleteFullResponse> response = vkApiClient.messages()
                .deleteFull(theMainBotGroupActor)
                .peerId(userId)
                .cmids(conversationMessageId)
                .deleteForAll(true)
                .execute();

        if(response==null){
            log.warn("deleteFull method returned null List<DeleteFullResponse>");
            return false;
        }
        return response.stream().filter(DeleteFullResponseItem::isResponse)
                .map(DeleteFullResponseItem::getConversationMessageId)
                .findFirst()
                .isPresent();
    }

    public Set<Integer> batchDeleteMessagesInAConversation(@NonNull CommandRoutingData commandRoutingData, @NonNull List<Integer> messagesToDelete) throws ApiException, ClientException {
        long dataBaseChatId = commandRoutingData.getDataBaseChatId();
        long vkApiChatId = commandRoutingData.getVkApiChatId();

        if (messagesToDelete.isEmpty()) return Collections.emptySet();

        List<MessagesDeleteQueryWithFull> deleteQueries = new ArrayList<>();

        List<List<Integer>> cmidBatches = partitionList(messagesToDelete, MAX_CMIDS_IN_ONE_DELETION_METHOD_CALL);

        for(List<Integer> batchCmids : cmidBatches){
            MessagesDeleteQueryWithFull query = vkApiClient.messages()
                    .deleteFull(commandRoutingData.getExecutorBot())
                    .peerId(convertToPeerId(vkApiChatId))
                    .cmids(batchCmids)
                    .deleteForAll(true);
            deleteQueries.add(query);

            if(deleteQueries.size()>=MAX_QUERIES_IN_ONE_BATCH) break;
        }

        ExecuteBatchQuery batchQuery = vkApiClient.execute().batch(commandRoutingData.getExecutorBot(), deleteQueries.toArray(new MessagesDeleteQueryWithFull[0]));

        JsonElement batchResponse = batchQuery.execute();
        log.info("chat {}: batch deletion messages execute result: {}",dataBaseChatId, batchResponse);

        Type type = new TypeToken<List<List<DeleteFullResponse>>>(){}.getType();
        List<List<DeleteFullResponse>> nested = GSON.fromJson(batchResponse, type);
        List<DeleteFullResponse> flat = nested.stream().flatMap(List::stream).toList();

        Set<Integer> justDeletedByTheBot = flat.stream()
                .filter(DeleteFullResponseItem::isResponse)
                .map(DeleteFullResponseItem::getConversationMessageId)
                .collect(Collectors.toSet());

        messageLogService.markMessagesAsDeleted(dataBaseChatId, justDeletedByTheBot);
        return justDeletedByTheBot;
    }

    public Optional<String> getChatTitle(long chatId, @NonNull GroupActor executorBot) throws ClientException{

        GetConversationsByIdResponse response;

        try{
            response = vkApiClient.messages().getConversationsById(executorBot)
                    .peerIds(convertToPeerId(chatId))
                    .execute();
        }catch (ApiException e){
            log.info("couldn't get chat title. executor bot: {}, peerId: {}",executorBot.getGroupId(), convertToPeerId(chatId), e);
            return Optional.empty();
        }
        if(response==null||response.getItems()==null||response.getItems().isEmpty()){
            log.info("couldn't get chat title cause vk sent not full response: {}", response);
            return Optional.empty();
        }
        return Optional.of(response.getItems().get(0).getChatSettings().getTitle());
    }

    private <T> List<List<T>> partitionList(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }


}
