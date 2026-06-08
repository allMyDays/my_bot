package com.example.my_bot.resolver;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.config.CaffeineCacheManager;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.member.ParseMemberInputResult;
import com.example.my_bot.service.MemberService;
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

    private final Pattern MEMBER_MENTION_PATTERN = Pattern.compile("\\[(id|club)(\\d+)\\|[^]]+]");

    private static final Pattern MEMBER_URL_PATTERN = Pattern.compile(
            "(?:https?://)?(?:www\\.)?(?:m\\.)?(?:(?:vk\\.(?:com|ru))|vkontakte\\.ru)/(((id|club|public)\\d{1,11})|[a-zA-Z0-9_.]{2,32})");
    private final MemberService memberService;


    public Optional<Long> getMemberIdByStringInput(long chatId, @NonNull String userInput){

        userInput=userInput.trim();

        Matcher mentionMatcher = MEMBER_MENTION_PATTERN.matcher(userInput.toLowerCase());
        if(mentionMatcher.find()){
            String type = mentionMatcher.group(1); // "id" или "club"
            if(!isValidLong(mentionMatcher.group(2))){
                return Optional.empty();
            }
            long id = Long.parseLong(mentionMatcher.group(2));
            return Optional.of(type.equals("id")?id:-id);
        }

        Matcher urlMatcher= MEMBER_URL_PATTERN.matcher(userInput.toLowerCase());
        if(urlMatcher.find()){
            if(urlMatcher.group(2)!= null){  // id123, club45, public6789
                String prefix = urlMatcher.group(3);
                String fullMatch = urlMatcher.group(2);
                String numStr = fullMatch.substring(prefix.length());
                long id = Long.parseLong(numStr);
                if(prefix.equals("id")){
                    return Optional.of(id);
                }else{
                    return Optional.of(-id);
                }
            }else{  // durov
                String userNickname = urlMatcher.group(1);
                return cacheManager.getNicknameCache().get(userNickname,
                        k -> vkChatClient.getMemberIdByScreenName(userNickname));
            }
        }else if(userInput.length()>1){  // поиск по имени/фамилии
            return memberService.findCurrentMemberByFirstNameOrLastName(chatId, userInput);
        }
        return Optional.empty();
    }

    public ParseMemberInputResult getMemberIdByAnyInput(CommandMessageDto messageDto, int userIndex){

        ParseMemberInputResult result = new ParseMemberInputResult();
        Long targetMember = null;
        if(!messageDto.getReplyOrFwdMessages().isEmpty()){
            targetMember = messageDto.getReplyOrFwdMessages().get(0).getFromId();
            result.setFwdMessage(true);
        }else{
            if(messageDto.getFirstRowArguments().length>=(userIndex+1)){
                Optional<Long> memberOptional = getMemberIdByStringInput(messageDto.getChatId(), messageDto.getFirstRowArguments()[userIndex]);
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
    public static String[] splitFullCommandIntoTwoElements(@NonNull String fullCommand){
        return fullCommand.trim().split(" +", 2);
    }

    /**
     *
     * возвращает String[] с командой и со всеми аргументами которые пришли через пробел
     */
    public static String[] splitFullCommandIntoAllElements(@NonNull String fullCommand){
        return fullCommand.trim().split("\\s");
    }

    /**
     *
     * ищет нужный аргумент в команде под нужным порядковым числом
     */
    public static Optional<String> getRequiredCommandArgument(@NonNull String userCommand, int argNum) {
        if(argNum < 1) return Optional.empty();

        int len= userCommand.length();
        int wordCount= 0;
        int start= -1;
        int end= -1;

        for(int i = 0; i < len; i++){
            while(i < len && Character.isWhitespace(userCommand.charAt(i))){
                i++;
            }
            if(i >= len) break;
            wordCount++;
            if(wordCount== argNum){
                start= i;
                while(i < len && !Character.isWhitespace(userCommand.charAt(i))) {
                    i++;
                }
                end= i;
                return Optional.of(userCommand.substring(start, end));
            }
            while(i < len && !Character.isWhitespace(userCommand.charAt(i))) {
                i++;
            }
        }
        return Optional.empty();
    }


    
    









}





