package com.example.my_bot.command.commands.ban;


import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.member.ParseMemberInputResult;
import com.example.my_bot.enumeration.TimeZoneType;
import com.example.my_bot.enumeration.user.NameCase;
import com.example.my_bot.exception.ban.BanException;
import com.example.my_bot.exception.command.CommandException;
import com.example.my_bot.exception.member.MemberException;
import com.example.my_bot.resolver.UserInputResolver;
import com.example.my_bot.service.BanService;
import com.example.my_bot.service.GlobalUserService;
import com.example.my_bot.service.chat.ChatService;
import com.example.my_bot.utils.TimeUtils;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import java.time.Instant;
import java.util.Optional;

import static com.example.my_bot.constant.MessageConstant.*;
import static com.example.my_bot.enumeration.DefaultRole.SENIOR_MODERATOR;
import static com.example.my_bot.utils.ChatUtils.createMention;

@Slf4j
@RequiredArgsConstructor
@Command(mainCommandName = "забанить", alternativeCommandNames = {"бан", "ban"}, defaultRole = SENIOR_MODERATOR, eventable = true)
public class BanCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(20,60);

    private VkChatClient vkChatClient;

    private final ChatService chatService;

    private final BanService banService;

    private final UserInputResolver userInputResolver;

    private final GlobalUserService globalUserService;

    @Autowired
    @Lazy
    public void setVkChatClient(VkChatClient vkChatClient) {
        this.vkChatClient = vkChatClient;
    }



    @Override
    public void execute(CommandMessageDto messageDto) throws ClientException, ApiException {

        long chatId = messageDto.getChatId();
        long peerId = messageDto.getPeerId();
        String[] args = messageDto.getFirstRowArguments();
        long fromId = messageDto.getFromId();

        long memberToBan;
        String reason=null;
        Long banPeriodInSeconds=null;

        // варианты:
        // !бан @durov
        // !бан (пересланное смс)
        // !бан @durov 2 часа
        // !бан 2 часа (пересланное смс)

        ParseMemberInputResult inputResult = userInputResolver.getMemberIdByAnyInput(messageDto, 0);
        if(inputResult.getMemberId().isPresent()){
            memberToBan = inputResult.getMemberId().get();
            if(args.length>=2){ // временный бан
                boolean isFwd = inputResult.isFwdMessage();
                if(!isFwd&&args.length<3){   // указали ссылку на участника, но недостаточно аргументов для периода бана
                    vkChatClient.sendText(NOT_ENOUGH_ARGUMENTS_MESSAGE,peerId, true);
                    return;
                }
                banPeriodInSeconds = TimeUtils.toSecondsFromString(args[isFwd?0:1],args[isFwd?1:2]).orElse(null); //!бан @durov 2 часа или !бан 2 часа (пересланное смс)
                if(banPeriodInSeconds==null){
                    vkChatClient.sendText(INVALID_TIME_PERIOD_MESSAGE,peerId, true);
                    return;
                }
            }else{   // вечный бан
                banPeriodInSeconds = chatService.getDefaultBanPeriod(chatId).orElse(null);
            }
        }else{
            vkChatClient.sendText(MEMBER_ARGUMENT_ABSENTS, peerId,true);
            return;
        }
        String [] rows = messageDto.getAllRows();
        if(rows.length>=2) reason = rows[1];

       Optional<Instant> bannedUntil= Optional.empty();

       try{
           bannedUntil = banService.createMemberBan(chatId, memberToBan, reason, banPeriodInSeconds,fromId);
           vkChatClient.kickOneChatMember(chatId, memberToBan);
       }catch (CommandException | MemberException | BanException e){
           vkChatClient.sendText(e.getMessage(),peerId, true);
           return;
       } catch (ApiException e){
           if(e.getCode()!=935){    // 935 - скорее всего дали бан тому, кого в чате никогда не было, чтобы он не смог присоединиться
             vkChatClient.sendText("Пользователь забанен, но его не удалось исключить из чата. "+e.getMessage(),peerId, true);
             return;
           }
       }

       TimeZoneType chatTimeZone = chatService.getChatTimeZone(chatId);
       String message = createMention(memberToBan)+"(✅Пользователь) был забанен в чате "+
               bannedUntil.map(instant -> "до "+TimeUtils.getStringDateTimeWithTimeZone(instant, chatTimeZone)).orElse("навечно.");

       if(!messageDto.isEventOrTimerMode()){
           message+="\nМодератор: %s(%s)".formatted(
               createMention(fromId),
               globalUserService.getUserNameInRequiredCase(fromId, NameCase.NOMINATIVE).orElse("этот участник")
           );
       }

       if(reason!=null){
           message+="\nПричина: "+reason;
       }

       vkChatClient.sendText(message,peerId, true);

    }
}
