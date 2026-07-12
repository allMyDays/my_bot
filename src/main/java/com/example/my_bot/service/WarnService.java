package com.example.my_bot.service;

import com.example.my_bot.dto.warn.CreateWarnResult;
import com.example.my_bot.entity.WarnEntity;
import com.example.my_bot.exception.command.CannotApplyThisCommandToYourselfException;
import com.example.my_bot.exception.member.UserNeverBeenInChatException;
import com.example.my_bot.repository.WarnRepository;
import com.example.my_bot.service.chat.ChatService;
import jakarta.annotation.Nullable;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class WarnService {

    private final WarnRepository warnRepository;
    private final MemberService memberService;
    private final ChatService chatService;

    public static final long MIN_WARN_TIME_PERIOD_SEC = 60;
    public static final long MAX_WARN_TIME_PERIOD_SEC = 94_608_000;
    public static final int MAX_WARN_QUANTITY = 100;
    public static final int MIN_WARN_QUANTITY = 2;

    public static final int DEFAULT_CHAT_WARN_QUANTITY = 3;


    @Transactional
    public CreateWarnResult createNewWarn(long chatId, long memberId, @Nullable String reason, @Nullable Long timePeriodSec, long fromId){

        if(memberId==fromId) throw new CannotApplyThisCommandToYourselfException();
        if(memberService.getCachedMemberInfo(chatId,memberId).isEmpty()) throw new UserNeverBeenInChatException(memberId);

        memberService.checkMemberInteractionAbility(chatId, fromId, memberId,true);

        CreateWarnResult result = new CreateWarnResult();
        Instant now = Instant.now();
        Instant expiresAt =null;

        result.setNewWarnQuantity(warnRepository.countActiveMemberWarns(chatId, memberId, now)+1);
        result.setMaxWarnQuantity(chatService.getWarnMaxQuantity(chatId));

        if(result.getNewWarnQuantity()>=result.getMaxWarnQuantity()){
            warnRepository.deleteAllMemberWarns(chatId, memberId);
            return result.setWarnLimitReached(true);
        }

        if(timePeriodSec!=null){
            if(timePeriodSec<MIN_WARN_TIME_PERIOD_SEC) timePeriodSec = MIN_WARN_TIME_PERIOD_SEC;
            if(timePeriodSec>MAX_WARN_TIME_PERIOD_SEC) timePeriodSec = MAX_WARN_TIME_PERIOD_SEC;
            expiresAt = now.plusSeconds(timePeriodSec);
            result.setExpiresAt(expiresAt);
        }

        reason = reason==null||(reason = reason.trim()).isEmpty() ? null : reason;

        WarnEntity newWarn = new WarnEntity();
        newWarn.setChatId(chatId);
        newWarn.setMemberId(memberId);
        newWarn.setCreatedAt(now);
        newWarn.setExpiresAt(expiresAt);
        newWarn.setReason(reason);
        newWarn.setGivenBy(fromId);

        warnRepository.save(newWarn);
        return result;
    }

    @Transactional
    public boolean deleteLastMemberWarn(long chatId, long memberId, long fromId){

        if(memberId==fromId) throw new CannotApplyThisCommandToYourselfException();
        memberService.checkMemberInteractionAbility(chatId, fromId, memberId,true);

        int deletedRows = warnRepository.deleteLastMemberWarn(chatId, memberId);
        return deletedRows>0;
    }

    @Transactional
    public void deleteWarnById(long id, long fromId){

        warnRepository.findById(id).ifPresent(warn->{
            if(warn.getMemberId()==fromId) throw new CannotApplyThisCommandToYourselfException();
            memberService.checkMemberInteractionAbility(warn.getChatId(), fromId, warn.getMemberId(),true);
        });
    }

    @Scheduled(fixedRate = 1_800_000)
    protected void deleteExpiredWarns(){
        warnRepository.deleteExpiredWarns(Instant.now());
    }



}
