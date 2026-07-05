package com.example.my_bot.command.commands.submanager;


import com.example.my_bot.annotation.Command;
import com.example.my_bot.cache.value.callback.GroupIdAndChatId;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.client.VkCommunityClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CaffeineCacheManager;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.command.CommandRoutingData;
import com.example.my_bot.dto.submanager.SubmanagerDto;
import com.example.my_bot.enumeration.command.CommandExecutionStatus;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.resolver.UserInputResolver;
import com.example.my_bot.service.MemberService;
import com.example.my_bot.service.submanager.SubmanagerActionService;
import com.example.my_bot.service.submanager.SubmanagerService;
import com.example.my_bot.utils.ChatUtils;
import com.example.my_bot.utils.SubmanagerUtils;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;

import java.util.Optional;
import java.util.Set;

import static com.example.my_bot.constant.MessageConstant.NOT_ENOUGH_ARGUMENTS_MESSAGE;
import static com.example.my_bot.enumeration.command.CommandExecutionStatus.*;
import static com.example.my_bot.enumeration.DefaultRole.SENIOR_ADMINISTRATOR;
import static com.example.my_bot.enumeration.chat.AdminChatCommandExecutionMode.ALL_BOUND_CHATS_AT_ONCE;
import static com.example.my_bot.utils.GroupUtils.createPrivateMessagesLink;
import static com.example.my_bot.utils.TextUtils.createMention;

@Slf4j
@Command(mainCommandName = "субменеджер", alternativeCommandNames = {"submanager", "newsub"}, defaultRole = SENIOR_ADMINISTRATOR, eventable = false, adminChatCommandExecutionMode = ALL_BOUND_CHATS_AT_ONCE)
public class SubmanagerCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(30,60*3);

    private final VkChatClient vkChatClient;
    private final VkCommunityClient vkCommunityClient;
    private final MessageMapper messageMapper;
    private final CaffeineCacheManager cacheManager;
    private final SubmanagerService submanagerService;
    private final UserInputResolver userInputResolver;
    private final SubmanagerActionService submanagerActionService;
    private final MemberService memberService;

    private final long theMainBotId;
    private final GroupActor theMainBotGroupActor;
    private final static String REMOVE_ARGUMENT = "удалить";



    public SubmanagerCommand(@Lazy VkChatClient vkChatClient,
                             @Lazy VkCommunityClient vkCommunityClient,
                             MessageMapper messageMapper,
                             CaffeineCacheManager cacheManager,
                             SubmanagerService submanagerService,
                             SubmanagerActionService submanagerActionService,
                             @Value("${vk.main-bot.id}") long theMainBotId,
                             @Qualifier("theMainBotGroupActor") GroupActor theMainBotGroupActor,
                             UserInputResolver userInputResolver,
                             MemberService memberService){

        this.vkChatClient = vkChatClient;
        this.vkCommunityClient = vkCommunityClient;
        this.messageMapper = messageMapper;
        this.cacheManager = cacheManager;
        this.submanagerService = submanagerService;
        this.submanagerActionService = submanagerActionService;
        this.theMainBotId = theMainBotId;
        this.theMainBotGroupActor = theMainBotGroupActor;
        this.userInputResolver = userInputResolver;
        this.memberService = memberService;
    }


    @Override
    public CommandExecutionStatus execute(CommandMessageDto commandMessage) throws ClientException, ApiException {

        String[] args = commandMessage.getFirstRowArguments();
        long fromId = commandMessage.getFromId();
        CommandRoutingData routingData = commandMessage.getCommandRoutingData();
        long dataBaseChatId = routingData.getDataBaseChatId();
        long vkApiChatId = routingData.getVkApiChatId();

        SendMessageDto sendMessage = messageMapper.toSendMessageDto(true, commandMessage);

        if(args.length==0){
            sendMessage.setText(NOT_ENOUGH_ARGUMENTS_MESSAGE);
            vkChatClient.sendText(sendMessage);
            return ARGUMENT_VALIDATION_ERROR;
        }
        if(args[0].equalsIgnoreCase(REMOVE_ARGUMENT)){
            // !субменеджер удалить
            if(!submanagerService.isSubmanager(routingData.getExecutorBot())){
                sendMessage.setText("К текущему чату не привязан субменеджер.");
                vkChatClient.sendText(sendMessage);
                return BUSINESS_LOGIC_ERROR;
            }
            memberService.synchronizeChatMembers(routingData);
            if(!memberService.isChatAdmin(routingData.getDataBaseChatId(), -theMainBotId)){
                sendMessage.setText(
                        "Чтобы отвязать субменеджер от текущей беседы, в ней должен находиться %s(Чат-менеджер) с правами администратора.".formatted(createMention(-theMainBotId))
                );;
                vkChatClient.sendText(sendMessage);
                return BUSINESS_LOGIC_ERROR;
            }
            submanagerActionService.handleSubmanagerUnBinding(dataBaseChatId,routingData.getExecutorBot(),vkApiChatId);
            return SUCCESS;
        }
        // !субменеджер @apiclub
        Optional<Long> memberArgument = userInputResolver.getMemberIdByStringInput(dataBaseChatId, args[0]);

        if(memberArgument.isEmpty()||!ChatUtils.isGroupId(memberArgument.get())){
            sendMessage.setText("Если хотите установить субменеджера, первым аргументом должна быть ссылка/упоминание сообщества.");
            vkChatClient.sendText(sendMessage);
            return BUSINESS_LOGIC_ERROR;
        }
        long groupId = memberArgument.get();

        SubmanagerDto subInfo = submanagerService.getOptionalSubmanager(groupId).orElse(null);
        if(subInfo==null){
            sendMessage.setText(
                    "Указанное сообщество не является субменеджером. Если вы администратор этого сообщества, предоставьте токен от него в ЛС чат-менеджеру: %s"
                            .formatted(createPrivateMessagesLink(-theMainBotId))
            );
            vkChatClient.sendText(sendMessage);
            return BUSINESS_LOGIC_ERROR;
        }
        if(submanagerService.isSubmanager(routingData.getExecutorBot())){
            sendMessage.setText("К текущему чату уже привязан %s(этот субменеджер). Отвяжите его, если хотите привязать другое сообщество.".formatted(createMention(-routingData.getExecutorBot().getGroupId())));
            vkChatClient.sendText(sendMessage);
            return BUSINESS_LOGIC_ERROR;
        }
        memberService.synchronizeChatMembers(routingData);
        if(!memberService.isChatAdmin(dataBaseChatId, groupId)){
            sendMessage.setText(
                    "Если хотите сделать %s(указанное сообщество) субменеджером, оно должно находиться в чате с правами администратора.".formatted(createMention(groupId))
            );
            vkChatClient.sendText(sendMessage);
            return BUSINESS_LOGIC_ERROR;
        }
        Set<Long> communityAdmins;
        try{
            communityAdmins = vkCommunityClient.getCommunityAdministrators(subInfo.getGroupId(), subInfo.getToken());
        }catch (ApiException e){
            log.warn("fail get submanager {} community admins by token",groupId, e);
            sendMessage.setText("Не удалось получить информацию о администраторах указанного сообщества-субменеджера. Попробуйте позже.");
            vkChatClient.sendText(sendMessage);
            return VK_API_ERROR;
        }
        if(!communityAdmins.contains(fromId)){
            sendMessage.setText("Вы не являетесь администратором указанного сообщества.");
            vkChatClient.sendText(sendMessage);
            return BUSINESS_LOGIC_ERROR;
        }
        String newBindingCode = SubmanagerUtils.generateNewBindingCode(subInfo.getGroupId());

        cacheManager.getBindingSubmanagerDataCache().put(
                newBindingCode, new GroupIdAndChatId(subInfo.getGroupId(), routingData.getDataBaseChatId())
        );

        sendMessage.setText(newBindingCode);
        sendMessage.setResponderBot(theMainBotGroupActor);
        sendMessage.setResponsePeerId(ChatUtils.convertToPeerId(routingData.getDataBaseChatId()));
        sendMessage.setReplyToMessageId(false);
        sendMessage.setDoNotSendTheMessage(false);

        vkChatClient.sendText(sendMessage);
        return SUCCESS;
    }
}
