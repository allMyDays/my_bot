package com.example.my_bot.command.commands.warn;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.member.ParseMemberInputResult;
import com.example.my_bot.entity.BanEntity;
import com.example.my_bot.entity.WarnEntity;
import com.example.my_bot.enumeration.TimeZoneType;
import com.example.my_bot.enumeration.command.CommandExecutionStatus;
import com.example.my_bot.enumeration.user.NameCase;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.resolver.UserInputResolver;
import com.example.my_bot.service.GlobalUserService;
import com.example.my_bot.service.WarnService;
import com.example.my_bot.service.chat.ChatService;
import com.example.my_bot.utils.TimeUtils;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import org.springframework.context.annotation.Lazy;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.example.my_bot.constant.MessageConstant.MEMBER_ARGUMENT_ABSENTS;
import static com.example.my_bot.enumeration.DefaultRole.MODERATOR;
import static com.example.my_bot.enumeration.chat.AdminChatCommandExecutionMode.ONLY_SINGLE_BOUND_CHAT_AT_ONCE;
import static com.example.my_bot.enumeration.command.CommandExecutionStatus.ARGUMENT_VALIDATION_ERROR;
import static com.example.my_bot.enumeration.command.CommandExecutionStatus.SUCCESS;
import static com.example.my_bot.utils.TextUtils.createMention;

@Command(mainCommandName = "предлист", alternativeCommandNames = {"преды","warnings"}, defaultRole = MODERATOR, eventable = true, adminChatCommandExecutionMode = ONLY_SINGLE_BOUND_CHAT_AT_ONCE)
public class WarnListShowCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(7,60*2);

    private final VkChatClient vkChatClient;
    private final MessageMapper messageMapper;
    private final GlobalUserService globalUserService;
    private final WarnService warnService;
    private final ChatService chatService;
    private final UserInputResolver userInputResolver;

    public WarnListShowCommand(@Lazy VkChatClient vkChatClient, MessageMapper messageMapper, GlobalUserService globalUserService, WarnService warnService, ChatService chatService, UserInputResolver userInputResolver) {
        this.warnService = warnService;
        this.chatService = chatService;
        this.vkChatClient = vkChatClient;
        this.messageMapper = messageMapper;
        this.globalUserService = globalUserService;
        this.userInputResolver = userInputResolver;
    }

    @Override
    public CommandExecutionStatus execute(CommandMessageDto commandMessage) throws ClientException, ApiException {

        long dataBaseChatId = commandMessage.getCommandRoutingData().getDataBaseChatId();
        TimeZoneType chatTimeZone = chatService.getChatTimeZone(dataBaseChatId);
        SendMessageDto sendMessage = messageMapper.toSendMessageDto(commandMessage);

        long memberToCheck;

        ParseMemberInputResult inputResult = userInputResolver.getMemberIdByAnyInput(commandMessage, 0);
        if(inputResult.getMemberId().isPresent()){
            memberToCheck = inputResult.getMemberId().get();
        }
        else{
            sendMessage.setText(MEMBER_ARGUMENT_ABSENTS);
            vkChatClient.sendText(sendMessage);
            return ARGUMENT_VALIDATION_ERROR;
        }

        StringBuilder sb = new StringBuilder(
                "Предупреждения %s(%s):\n\n".formatted(createMention(memberToCheck), globalUserService.getUserFullNameInRequiredCase(memberToCheck, NameCase.GENITIVE))
        );

        List<WarnEntity> warnings = warnService.getMemberWarningsSortedInDesc(dataBaseChatId, memberToCheck);

        Map<Long, String> givenByMap = globalUserService.getUserFullNamesInRequiredCase(
                warnings.stream()
                        .map(WarnEntity::getGivenBy)
                        .collect(Collectors.toSet()),
                NameCase.NOMINATIVE
        );

        int counter = 1;
        for(WarnEntity waring: warnings){
            sb.append("%d. Выдан пользователем %s(%s) %s %s %s"
                    .formatted(
                            counter++,
                            createMention(waring.getGivenBy()),
                            givenByMap.get(waring.getGivenBy()),
                            TimeUtils.getFormattedStringDateTime(waring.getCreatedAt(), chatTimeZone),
                            waring.getExpiresAt()==null?"":"\n    ⏳Истекает "+TimeUtils.getFormattedStringDateTime(waring.getExpiresAt(), chatTimeZone),
                            waring.getReason()==null?"":"\n    ❓Причина: "+waring.getReason()
                    )
            );
            sb.append("\n");
        }

        if(!warnings.isEmpty()){
            sb.append("\nВремя выдачи/истечения указано по ").append(chatTimeZone.getStringType()).append(".");
        }

        vkChatClient.sendText(messageMapper.toSendMessageDto(sb.toString(),commandMessage));

        return SUCCESS;
    }

}
