package com.example.my_bot.command.commands.permission;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.permission.MemberPermissionSettingResult;
import com.example.my_bot.enumeration.user.NameCase;
import com.example.my_bot.exception.command.CommandException;
import com.example.my_bot.exception.member.MemberException;
import com.example.my_bot.exception.permission.PermissionException;
import com.example.my_bot.exception.role.RoleException;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.resolver.UserInputResolver;
import com.example.my_bot.service.chat.ChatService;
import com.example.my_bot.service.permission.MemberPermissionService;
import com.example.my_bot.service.GlobalUserService;
import com.example.my_bot.utils.ChatUtils;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.example.my_bot.constant.MessageConstant.*;
import static com.example.my_bot.enumeration.DefaultRole.ADMINISTRATOR;
import static com.example.my_bot.utils.TextUtils.*;

@Slf4j
@Command(mainCommandName = "правоюзера", alternativeCommandNames = {"правоюзеру","userallow"}, defaultRole = ADMINISTRATOR, eventable = false)
@RequiredArgsConstructor
public class MemberPermissionCreateCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(5,60);

    private final VkChatClient vkChatClient;

    private final ChatService chatService;

    private final UserInputResolver userInputResolver;

    private final MemberPermissionService memberPermissionService;

    private final GlobalUserService userService;

    private final MessageMapper messageMapper;


    @Override
    public void execute(CommandMessageDto messageDto) throws ClientException, ApiException {

        long chatId = messageDto.getChatId();
        String[] args = messageDto.getFirstRowArguments();

        SendMessageDto sendMessage = messageMapper.toSendMessageDto("",true, messageDto);

        if(args.length<2){
            sendMessage.setText(NOT_ENOUGH_ARGUMENTS_MESSAGE);
            vkChatClient.sendText(sendMessage);
            return;
        }
        Optional<Long> targetUserId = userInputResolver.getMemberIdByStringInput(args[0].trim());
        if(targetUserId.isEmpty()){
            sendMessage.setText(MEMBER_LINK_IS_NOT_CORRECT);
            vkChatClient.sendText(sendMessage);
            return;
        }

        MemberPermissionSettingResult permissionResult=null;

        boolean allow=true;
        if(args.length==3&&args[2].equalsIgnoreCase("запретить")){
            allow=false;
        }

        try{
            Set<String> userCommandsToProcess = new HashSet<>();
            userCommandsToProcess.add(args[1].trim());
                for(int i=1;i<messageDto.getAllRows().length;i++){
                    userCommandsToProcess.add(messageDto.getAllRows()[i].trim());
                }
                permissionResult = memberPermissionService.allowOrForbidCommandForMember(
                        chatId, messageDto.getFromId(), userCommandsToProcess, targetUserId.get(), allow);

        }catch (PermissionException | RoleException | CommandException | MemberException e){
            sendMessage.setText(e.getMessage());
            vkChatClient.sendText(sendMessage);
            return;
        }
        if(permissionResult==null){
            log.error("chat {} error: permissionResult is null after executing method allowOrForbidCommandForMember", chatId);
            sendMessage.setText("Произошла ошибка при попытке обработать команды.");
            vkChatClient.sendText(sendMessage);
            return;
        }
        char chatPrefix = chatService.getChatPrefix(chatId).orElse(ChatUtils.DEFAULT_CHAT_PREFIX);

        StringBuilder result = new StringBuilder();
        String cmdPrefix = "⚙ " + chatPrefix;
        String userMention = createMention(targetUserId.get());

        String userName = userService.getUserNameInRequiredCase(targetUserId.get(), NameCase.INSTRUMENTAL)
                .orElse("этим участником");

        appendSection(result, permissionResult.getAccepted(), cmdPrefix,
                "✅Команды:\n", "%s\nТеперь "+(allow?"могут персонально":"никогда не могут")
                        +" применяться %s("+userName+") независимо от его роли.", userMention);
        appendSection(result, permissionResult.getHasRequiredPermissionAlready(), cmdPrefix,
                "‼Команды:\n", "%s\nУже "+(allow?"разрешены":"запрещены")+" персонально %s(этому участнику).",userMention);
        appendSection(result, permissionResult.getForbiddenToEdit(), cmdPrefix,
                "\uD83D\uDEABКоманды:\n", "%s\nНедоступны вам для редактирования (сейчас их роль доступа выше Вашей роли).", userMention);
        appendSection(result, permissionResult.getNotEnoughSpaceToAddNew(), cmdPrefix,
                "\uD83D\uDEABДля команд:\n", "%s\nНе хватило свободного места для добавления.", userMention);
        appendSection(result, permissionResult.getNotFound(), "❓",
                "❌Аргументы:\n", "%s\nНе являются командами или написаны с опечатками.", userMention);

        sendMessage.setText(result.toString());
        vkChatClient.sendText(sendMessage);

    }

    private void appendSection(StringBuilder result,
                               Collection<String> items,
                               String itemPrefix,
                               String title,
                               String messageTemplate,
                               String userMention) {
        if (items.isEmpty()) return;
        if (!result.isEmpty()) result.append("\n\n");
        String itemsFormatted = items.stream()
                .map(item -> itemPrefix + item)
                .collect(Collectors.joining("\n"));
        result.append(title);
        result.append(String.format(messageTemplate, itemsFormatted, userMention));
    }

}
