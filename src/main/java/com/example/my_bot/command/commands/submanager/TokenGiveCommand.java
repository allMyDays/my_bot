package com.example.my_bot.command.commands.submanager;


import com.example.my_bot.annotation.Command;
import com.example.my_bot.cache.value.callback.SecretKeyAndConfirmationCodeAndCompletableFuture;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.client.VkCommunityClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CaffeineCacheManager;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.submanager.SubmanagerDto;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.service.submanager.SubmanagerService;
import com.example.my_bot.utils.ChatUtils;
import com.example.my_bot.utils.GroupUtils;
import com.example.my_bot.vk.enumeration.GroupTokenPermissionType;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import static com.example.my_bot.constant.MessageConstant.NOT_ENOUGH_ARGUMENTS_MESSAGE;
import static com.example.my_bot.enumeration.DefaultRole.MEMBER;
import static com.example.my_bot.vk.enumeration.GroupTokenPermissionType.*;

@Slf4j
@Command(mainCommandName = "токенгруппы", alternativeCommandNames = {"grouptoken"}, defaultRole = MEMBER, eventable = false, onlyForConversations = false)
public class TokenGiveCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(3,60*3);

    private final VkChatClient vkChatClient;
    private final VkCommunityClient vkCommunityClient;
    private final MessageMapper messageMapper;
    private final CaffeineCacheManager cacheManager;
    private final SubmanagerService submanagerService;

    private final long theMainBotId;
    private final GroupActor theMainBotGroupActor;

    private final String callBackServerUrl;
    private final String callBackServerTitle;
    private final String callBackApiVersion;

    private final int WAITING_FOR_SUCCESS_CONFIRMATION_TIME_PERIOD_SEC = 120;
    private final static Set<GroupTokenPermissionType> REQUIRED_TOKEN_PERMISSIONS = Set.of(COMMUNITY_MANAGEMENT, MESSAGES, PHOTOS, DOCS);


    public TokenGiveCommand(@Lazy VkChatClient vkChatClient,
                            @Lazy VkCommunityClient vkCommunityClient,
                            MessageMapper messageMapper,
                            CaffeineCacheManager cacheManager,
                            SubmanagerService submanagerService,
                            @Value("${vk.main-bot.id}") long theMainBotId,
                            @Qualifier("theMainBotGroupActor") GroupActor theMainBotGroupActor,
                            @Value("${vk.submanager.callback.server.url}") String callBackServerUrl,
                            @Value("${vk.submanager.callback.server.title}") String callBackServerTitle,
                            @Value("${vk.submanager.callback.server.api-version}") String callBackApiVersion){

        this.vkChatClient = vkChatClient;
        this.vkCommunityClient = vkCommunityClient;
        this.messageMapper = messageMapper;
        this.cacheManager = cacheManager;
        this.submanagerService = submanagerService;
        this.theMainBotId = theMainBotId;
        this.theMainBotGroupActor = theMainBotGroupActor;
        this.callBackServerUrl = callBackServerUrl;
        this.callBackServerTitle = callBackServerTitle;
        this.callBackApiVersion = callBackApiVersion;
    }


    @Override
    public void execute(CommandMessageDto messageDto) throws ClientException, ApiException {

        String[] args = messageDto.getFirstRowArguments();
        long fromId = messageDto.getFromId();

        SendMessageDto sendMessage = messageMapper.toSendMessageDto(true, messageDto);

        if(!ChatUtils.isPersonalChat(messageDto.getCommandRoutingData().getOriginalEventPeerId())){
            sendMessage.setText("Данную команду можно использовать только в личных сообщениях Чат-менеджера: "+ GroupUtils.createPrivateMessagesLink(theMainBotId));
            vkChatClient.sendText(sendMessage);
            return;
        }
        if(args.length==0){
            sendMessage.setText(NOT_ENOUGH_ARGUMENTS_MESSAGE);
            vkChatClient.sendText(sendMessage);
            return;
        }
        String groupToken = args[0];

        Long groupId = vkCommunityClient.getCommunityIdByToken(groupToken).orElse(null);
        if(groupId==null){
            sendMessage.setText("Указанный вами токен не является валидным действующим токеном сообщества.");
            vkChatClient.sendText(sendMessage);
            return;
        }
        Set<GroupTokenPermissionType> tokenPermissions;
        try{
            tokenPermissions = vkCommunityClient.getTokenPermissions(groupToken);
        }catch (ApiException e){
            log.warn("tokenGiveCommand: fail getting token permissions for valid token. user: {}", fromId, e);
            sendMessage.setText("Не удалось получить список прав, доступных вашему токену сообщества."+e.getMessage());
            vkChatClient.sendText(sendMessage);
            return;
        }
        Set<GroupTokenPermissionType> missingTokenPermissions = REQUIRED_TOKEN_PERMISSIONS.stream()
                .filter(p->!tokenPermissions.contains(p))
                .collect(Collectors.toSet());

        if(!missingTokenPermissions.isEmpty()){
            sendMessage.setText(
                    "Для полноценной работы субменеджера, вашему токену не хватает следующих прав:\n"+
                            missingTokenPermissions.stream()
                                    .map(GroupTokenPermissionType::getCyrillicName)
                                    .collect(Collectors.joining(", "))

                    );
            vkChatClient.sendText(sendMessage);
            return;
        }

        Optional<SubmanagerDto> existingSub = submanagerService.getOptionalSubmanager(groupId);
        if(existingSub.isPresent()){
            boolean deleted = vkCommunityClient.deleteCallbackServerByToken(groupId, existingSub.get().getToken(), existingSub.get().getServerId());
            if(!deleted){
                sendMessage.setText("Я ранее уже устанавливал callback сервер в эту группу, но мне не удалось его удалить. Пожалуйста, попробуйте позже.");
                vkChatClient.sendText(sendMessage);
                return;
            }
        }

        String callBackConfirmationCode;
        try{
            callBackConfirmationCode = vkCommunityClient.getCallbackConfirmationCodeByToken(groupId, groupToken);
        }catch (ApiException e){
            log.warn("tokenGiveCommand: fail getting confirmation code for callback using group token with required permission. userId: {}", fromId, e);
            sendMessage.setText("Не удалось получить строку, необходимую для подтверждения адреса сервера Callback API в вашем сообществе. "+e.getMessage());
            vkChatClient.sendText(sendMessage);
            return;
        }

        Integer newCallBackServerId;
        String newSecretKey = GroupUtils.generateCBServerSecretKey();

        CompletableFuture<Boolean> futureConfirmationResult = new CompletableFuture<>();
        cacheManager.getConfirmationCallbackUrlCache().put(
                groupId,
                new SecretKeyAndConfirmationCodeAndCompletableFuture(newSecretKey, callBackConfirmationCode, futureConfirmationResult)
        );

        try{
            newCallBackServerId = vkCommunityClient.addCallbackServerByToken(groupId, groupToken, callBackServerUrl, callBackServerTitle, newSecretKey);
        } catch (ApiException e){
            log.warn("tokenGiveCommand: fail add new callback server using group token with required permission. userId: {}", fromId, e);
            sendMessage.setText("Не удалось добавить Callback API сервер в ваше сообщество. "+e.getMessage());
            vkChatClient.sendText(sendMessage);
            return;
        }

        sendMessage.setText("Подождите...");
        List<Integer> sent = vkChatClient.sendText(sendMessage);

        boolean isSuccessConfirmation=false;
        try{
            isSuccessConfirmation = futureConfirmationResult.get(WAITING_FOR_SUCCESS_CONFIRMATION_TIME_PERIOD_SEC, TimeUnit.SECONDS);
        }catch (TimeoutException | InterruptedException | ExecutionException e){
            log.warn("tokenGiveCommand: fail waiting for callback server url confirmation, user: {} ", messageDto.getFromId(), e);
        }

        if(!sent.isEmpty()){
            try {
                vkChatClient.deleteOneMessageInTheMainBotPrivateMessages(fromId, sent.get(0));
            }catch (Exception e){
                log.warn("tokenGiveCommand: fail delete sent message «Подождите...». user: {} ", messageDto.getFromId(), e);
            }
        }

        if(!isSuccessConfirmation){
            log.warn("tokenGiveCommand: CompletableFuture<Boolean> is false result. user: {}",fromId);
            sendMessage.setText("Не удалось подтвердить url адрес только что добавленного callback сервера.");
            vkChatClient.sendText(sendMessage);
            return;
        }

        boolean isSuccessConfiguration = false;
        try {
            isSuccessConfiguration = vkCommunityClient.setStandardCallbackSettingsForSubmanager(groupId, groupToken, newCallBackServerId, callBackApiVersion);
        }catch (ApiException e){
            log.warn("tokenGiveCommand: fail add standard callback settings for server that's just has been added using token with required permission. userId: {}", fromId, e);
        }
        if(!isSuccessConfiguration){
            log.warn("tokenGiveCommand: setStandardCallbackSettingsForSubmanager is false result. user: {}",fromId);
            sendMessage.setText("Я успешно добавил в ваше сообщество callback сервер с подтвержденным url адресом, но мне не удалось настроить этот сервер. Сообщите разработчику.");
            vkChatClient.sendText(sendMessage);
            return;
        }

        sendMessage.setText("Успешно! Ваше сообщество было полностью настроено для самостоятельной работы в многопользовательских беседах. Теперь можете приглашать его в свои чаты.");
        vkChatClient.sendText(sendMessage);

        submanagerService.createOrUpdateSubmanagerInfo(groupId, groupToken, newCallBackServerId, newSecretKey);

    }
}
