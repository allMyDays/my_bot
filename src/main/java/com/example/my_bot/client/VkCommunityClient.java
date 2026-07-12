package com.example.my_bot.client;

import com.example.my_bot.utils.GroupUtils;
import com.example.my_bot.vk.enumeration.GroupTokenPermissionType;
import com.example.my_bot.vk.CustomVkApiClient;
import com.google.gson.Gson;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ApiExtendedException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.objects.base.BoolInt;
import com.vk.api.sdk.objects.base.responses.BoolResponse;
import com.vk.api.sdk.objects.base.responses.OkResponse;
import com.vk.api.sdk.objects.groups.GetMembersFilter;
import com.vk.api.sdk.objects.groups.MemberRoleStatus;
import com.vk.api.sdk.objects.groups.responses.*;
import com.vk.api.sdk.objects.messages.responses.*;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

import static com.example.my_bot.utils.ChatUtils.*;
import static com.example.my_bot.vk.enumeration.CommunityErrorCode.CALLBACK_SERVER_IS_NOT_FOUND;
import static com.example.my_bot.vk.enumeration.CommunityErrorCode.NO_GROUP_MEMBERS_ACCESS;

@Component
@Slf4j
public class VkCommunityClient {
    private final CustomVkApiClient vkApiClient;
    private final GroupActor theMainBotGroupActor;
    private final long theBotId;
    private static final Gson GSON = new Gson();

    public VkCommunityClient(CustomVkApiClient vkApiClient, @Qualifier("theMainBotGroupActor") GroupActor theMainBotGroupActor){
        this.vkApiClient = vkApiClient;
        this.theMainBotGroupActor = theMainBotGroupActor;
        this.theBotId = theMainBotGroupActor.getGroupId();
    }

    public boolean canTheMainBotWriteToUser(long userId){
        try {
            IsMessagesFromGroupAllowedResponse response = vkApiClient.messages()
                    .isMessagesFromGroupAllowed(theMainBotGroupActor)
                    .groupId(theMainBotGroupActor.getGroupId())
                    .userId(userId)
                    .execute();

            BoolInt boolInt =  response.getIsAllowed();
            return boolInt==BoolInt.YES;
        } catch (ApiException | ClientException e) {
            log.error("Ошибка при проверке разрешения для user {}: {}", userId, e.getMessage());
            return false;
        }
    }

    public boolean isCommunityMember(long groupId, long userId) throws ClientException, ApiException {

        if(isGroupId(userId)) return false;
        try{
            BoolResponse response = vkApiClient.groups()
                    .isMember(theMainBotGroupActor)
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

    public Optional<Long> getCommunityIdByToken(@NonNull String groupToken) throws ClientException{

        if(!GroupUtils.isGroupToken(groupToken)){
            return Optional.empty();
        }

        GetByIdObjectResponse response;
        try{
            response= vkApiClient.groups()
                .getByIdObject(new GroupActor(null, groupToken))
                .execute();
        }catch (ApiException e){
            log.info("fail get groud id by group token ",e);
            return Optional.empty();
        }

        if(response==null||response.getGroups()==null||response.getGroups().isEmpty()){
            return Optional.empty();
        }
        return Optional.ofNullable(response.getGroups().get(0).getId());
    }

    public String getCallbackConfirmationCodeByToken(long groupId, @NonNull String groupToken) throws ClientException, ApiException {

       GetCallbackConfirmationCodeResponse response =  vkApiClient.groups().getCallbackConfirmationCode(new GroupActor(groupId, groupToken))
                .groupId(groupId)
                .execute();
       return response.getCode();
    }

    public int addCallbackServerByToken(long groupId,
                                                 @NonNull String groupToken,
                                                 @NonNull String serverUrl,
                                                 @NonNull String serverTitle,
                                                 @NonNull String secretKey) throws ClientException, ApiException{

        AddCallbackServerResponse response = vkApiClient.groups().addCallbackServer(new GroupActor(groupId, groupToken))
                .groupId(groupId)
                .url(serverUrl)
                .title(serverTitle)
                .secretKey(secretKey)
                .execute();

        return response.getServerId();
    }

    public boolean deleteCallbackServerByToken(long groupId, @NonNull String groupToken, int serverId) throws ClientException, ApiException{

        OkResponse response;
        try{
            response = vkApiClient.groups().deleteCallbackServer(new GroupActor(groupId, groupToken))
                .groupId(groupId)
                .serverId(serverId)
                .execute();
        }catch(ApiException e){
            if(CALLBACK_SERVER_IS_NOT_FOUND.getCodes().contains(e.getCode())){
                return true; // сервер ранее уже был удалён кем-то
            }
            log.warn("error of deletion server with id {} in community {}", serverId, groupId, e);
            return false;
        }
        return response==OkResponse.OK;
    }

    public boolean setStandardCallbackSettingsForSubmanager(long groupId, @NonNull String groupToken, int serverId, String apiVersion) throws ClientException, ApiException {

        OkResponse response = vkApiClient.groups().setCallbackSettings(new GroupActor(groupId, groupToken))
                .groupId(groupId)
                .serverId(serverId)
                .apiVersion(apiVersion)
                .messageNew(true)
                .messageReactionEvent(true)
                .wallPostNew(true)
                .execute();

        return response == OkResponse.OK;
    }

    public Set<GroupTokenPermissionType> getTokenPermissions(@NonNull String groupToken) throws ClientException, ApiException {

           GetTokenPermissionsResponse response = vkApiClient.groups().getTokenPermissions(new GroupActor(null, groupToken))
                    .execute();

           return response.getPermissions().stream()
                   .map(t->GroupTokenPermissionType.findByVkType(t.getName()).orElse(null))
                   .filter(Objects::nonNull)
                   .collect(Collectors.toSet());
    }

    public Set<Long> getAllCommunityAdministrators(long groupId, @NonNull String groupToken) throws ClientException, ApiException {

        groupId = Math.abs(groupId);

        GetMembersFilterResponse resp =  vkApiClient.groups().getMembersWithFilter(new GroupActor(groupId, groupToken), GetMembersFilter.MANAGERS)
                .groupId(String.valueOf(groupId))
                .execute();

        if(resp==null||resp.getItems()==null){
            log.warn("cannot get community {} administrators cause vk sent not full response {}", groupId, resp);
            return Collections.emptySet();
        }

        return resp.getItems().stream()
                .filter((m->m.getRole()==MemberRoleStatus.ADMINISTRATOR||m.getRole()==MemberRoleStatus.CREATOR))
                .map(m->(long)m.getId())
                .collect(Collectors.toSet());
    }

    public Optional<Long> getCommunityCreator(long groupId, @NonNull String groupToken) throws ClientException, ApiException {

        groupId = Math.abs(groupId);

        GetMembersFilterResponse resp =  vkApiClient.groups().getMembersWithFilter(new GroupActor(groupId, groupToken), GetMembersFilter.MANAGERS)
                .groupId(String.valueOf(groupId))
                .execute();

        if(resp==null||resp.getItems()==null){
            log.warn("cannot get community {} administrators cause vk sent not full response {}", groupId, resp);
            return Optional.empty();
        }

        return resp.getItems().stream()
                .filter((m->m.getRole()==MemberRoleStatus.CREATOR))
                .map(m->(long)m.getId())
                .findFirst();
    }


}
