package com.example.my_bot.command.commands;


import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.exception.member.MemberException;
import com.example.my_bot.exception.message.MessageException;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.resolver.UserInputResolver;
import com.example.my_bot.service.GlobalUserService;
import com.example.my_bot.service.MessageLogService;
import com.example.my_bot.service.chat.ChatService;
import com.example.my_bot.utils.TimeUtils;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.objects.messages.ForeignMessage;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;

import java.util.*;

import static com.example.my_bot.constant.MessageConstant.*;
import static com.example.my_bot.enumeration.DefaultRole.SENIOR_MODERATOR;

@Slf4j
@RequiredArgsConstructor
@Command(mainCommandName = "удаление", alternativeCommandNames = {"чистка", "delete"}, defaultRole = SENIOR_MODERATOR, eventable = true)
public class DeleteMessagesCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(4,60);

    private final MessageMapper messageMapper;
    private VkChatClient vkChatClient;
    private final ChatService chatService;
    private final UserInputResolver userInputResolver;
    private final GlobalUserService globalUserService;

    private final static long DEFAULT_DELETION_TIME_PERIOD_SEC = 604_800;
    private final static int MESSAGE_LIMIT_AT_ONE_USAGE = 500;
    private final static int SHOW_MESSAGE_BEFORE_DELETION_IF_MESSAGES_MORE_THAN = 100;

    public static final String DELETING_MESSAGES_GUIDE = """
            ⚙ Справка по использованию
            1) !удаление и [пересланные сообщения];
            2) !удаление @durov -- удалит все сообщения данного участника за неделю;
            3) !удаление @durov 5 часов -- удалит все сообщения данного участника за указанный период времени;
            4) !чистка 3 дня - удалит все сообщения не-администраторов за данный срок;
            5) Для событий: &delete. Например, «!ивент сообщение 100 !&delete» будет удалять все сообщения.
            """;
    private MessageLogService messageLogService;


    @Autowired
    @Lazy
    public void setVkChatClient(VkChatClient vkChatClient) {
        this.vkChatClient = vkChatClient;
    }


    @Override
    public void execute(CommandMessageDto messageDto) throws ClientException, ApiException {

        long chatId = messageDto.getChatId();
        String[] args = messageDto.getFirstRowArguments();
        long fromId = messageDto.getFromId();

        Long targetMember=null;
        Long timePeriodSec;

        // варианты:
        // !чистка - покажет справку по использованию команды
        // !чистка (пересланные сообщения) - удалит выбранные сообщения
        // !чистка @durov - удалит сообщения участника за неделю
        // !чистка @durov 2 часа - удалит сообщения участника за последние 2 часа
        // !чистка 3 дня - удалит все сообщения не-администраторов за данный срок

        SendMessageDto sendMessage = messageMapper.toSendMessageDto("",messageDto);

        List<Integer> messagesToDelete;
        long totalMessagesQuantity;


        if(args.length==0){
            // !чистка
            if(messageDto.getReplyOrFwdMessages().isEmpty()){
                sendMessage.setText(DELETING_MESSAGES_GUIDE);
                vkChatClient.sendText(sendMessage);
                return;
            }else{
                messagesToDelete = messageDto.getReplyOrFwdMessages().stream()
                        .map(ForeignMessage::getConversationMessageId)
                        .toList();
                totalMessagesQuantity = messagesToDelete.size();
            }
        }
        else if(args.length!=2){
            // либо [!чистка @durov] либо [!чистка @durov 2 часа]
            targetMember = userInputResolver.getMemberIdByStringInput(chatId, args[0]).orElse(null);
            if(targetMember==null){
                sendMessage.setText(MEMBER_LINK_IS_NOT_CORRECT);
                vkChatClient.sendText(sendMessage);
                return;
            }
            if(args.length==1){
                // !чистка @durov
                timePeriodSec = DEFAULT_DELETION_TIME_PERIOD_SEC;
            }else{
                // !чистка @durov 2 часа
               timePeriodSec = TimeUtils.toSecondsFromString(args[1],args[2]).orElse(null);
               if(timePeriodSec==null){
                   sendMessage.setText(INVALID_TIME_PERIOD_MESSAGE);
                   vkChatClient.sendText(sendMessage);
                   return;
               }
            }
            try{
                Page<Integer> result = messageLogService.findNotDeletedMessageIdsOfNotAChatAdminOwner(chatId, targetMember, timePeriodSec, MESSAGE_LIMIT_AT_ONE_USAGE);
                messagesToDelete = result.getContent();
                totalMessagesQuantity = result.getTotalElements();
            }catch (MemberException | MessageException e){
                sendMessage.setText(e.getMessage());
                vkChatClient.sendText(sendMessage);
                return;
            }

        }else{
            // !чистка 3 дня
            timePeriodSec = TimeUtils.toSecondsFromString(args[0],args[1]).orElse(null);
            if(timePeriodSec==null){
                sendMessage.setText(INVALID_TIME_PERIOD_MESSAGE);
                vkChatClient.sendText(sendMessage);
                return;
            }
            try{
                Page<Integer> result = messageLogService.findNotDeletedMessageIdsOfNotChatAdminOwners(chatId, timePeriodSec, MESSAGE_LIMIT_AT_ONE_USAGE);
                messagesToDelete = result.getContent();
                totalMessagesQuantity = result.getTotalElements();
            }catch (MemberException | MessageException e){
                sendMessage.setText(e.getMessage());
                vkChatClient.sendText(sendMessage);
                return;
            }
        }

        if(messagesToDelete.size()>SHOW_MESSAGE_BEFORE_DELETION_IF_MESSAGES_MORE_THAN){
            sendMessage.setText("Отправляю запрос на удаление %d из %d сообщений.."
                    .formatted(messagesToDelete.size(),totalMessagesQuantity)
            );
            vkChatClient.sendText(sendMessage);
        }

        Set<Integer> deletedMessages = vkChatClient.batchDeleteMessages(chatId, messagesToDelete);

        StringBuilder sb = new StringBuilder("✅Было успешно удалено %d из %d сообщений.\n".formatted(deletedMessages.size(), messagesToDelete.size()));
        if(deletedMessages.size()<messagesToDelete.size()){
            sb.append("Остальные %d сообщений вероятно уже были удалены".formatted(messagesToDelete.size()-deletedMessages.size()));
            if(targetMember==null||deletedMessages.isEmpty()){
                // удаление сообщений всех участников за срок либо конкретного чела
                sb.append(", либо сообщения принадлежат создателю/администратору чата");
            } sb.append(".");
        }
        sendMessage.setText(sb.toString());
        vkChatClient.sendText(sendMessage);
    }

    @Autowired
    public void setMessageLogService(MessageLogService messageLogService){
        this.messageLogService = messageLogService;
    }
}
