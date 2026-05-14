package com.example.my_bot.resolver;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.config.CaffeineCacheManager;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.member.ParseMemberInputResult;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.example.my_bot.utils.TextUtils.isValidLong;

@Component
@Slf4j
@RequiredArgsConstructor
public class UserInputResolver {

    private final CaffeineCacheManager cacheManager;

    private final VkChatClient vkChatClient;

    private final Pattern MEMBER_MENTION = Pattern.compile("\\[(id|club)(\\d+)\\|[^]]+]");

    private static final Pattern VK_URL_PATTERN = Pattern.compile(
            "(?:https?://)?(?:www\\.)?(?:m\\.)?(?:(?:vk\\.(?:com|ru))|vkontakte\\.ru)/(((id|club|public)\\d{1,11})|[a-zA-Z0-9_.]{2,32})");



    public Optional<Long> getMemberIdByStringInput(@NonNull String userInput){

        userInput=userInput.toLowerCase().trim();

        Matcher matcher = MEMBER_MENTION.matcher(userInput);
        if (matcher.find()) {
            String type = matcher.group(1); // "id" или "club"
            if(!isValidLong(matcher.group(2))){
                return Optional.empty();
            } long id = Long.parseLong(matcher.group(2));

            return Optional.of(type.equals("id")?id:(id*-1));
        }
        Matcher m = VK_URL_PATTERN.matcher(userInput);
        if (!m.find()) return Optional.empty();

        if (m.group(2) != null) {
            String prefix = m.group(3);
            String fullMatch = m.group(2);
            String numStr = fullMatch.substring(prefix.length());
            long id = Long.parseLong(numStr);
            if (prefix.equals("id")) {
                return Optional.of(id);
            } else {
                return Optional.of(-id);
            }
        } else {
            String userNickname = m.group(1);
            return cacheManager.getNicknameCache().get(userNickname,
                    k -> vkChatClient.getMemberIdByNickname(userNickname));

        }
    }

    public ParseMemberInputResult getMemberIdByAnyInput(CommandMessageDto messageDto, int userIndex){

        ParseMemberInputResult result = new ParseMemberInputResult();
        Long targetMember = null;
        if(messageDto.getReplyMessageOwnerId().isPresent()){
            targetMember = messageDto.getReplyMessageOwnerId().get();
            result.setFwdMessage(true);
        }else if(!messageDto.getFwdMessageOwnerIds().isEmpty()){
            targetMember = messageDto.getFwdMessageOwnerIds().get(0);
            result.setFwdMessage(true);
        }else{
            if(messageDto.getFirstRowArguments().length>=(userIndex+1)){
                Optional<Long> memberOptional = getMemberIdByStringInput(messageDto.getFirstRowArguments()[userIndex]);
                if(memberOptional.isPresent()){
                    targetMember = memberOptional.get();
                }
            }
        }
        result.setMemberId(targetMember);
        return result;
    }

    /**
     *
     * возвращает String[] с двумя ячейками: команду + все остальные аргументы
     */
    public static String[] splitFullCommand(@NonNull String fullCommand){
        return fullCommand.trim().split(" +", 2);
    }









}





