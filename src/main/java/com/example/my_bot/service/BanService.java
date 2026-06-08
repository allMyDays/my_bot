package com.example.my_bot.service;

import com.example.my_bot.cache.key.ChatIdAndMemberIdKey;
import com.example.my_bot.config.CaffeineCacheManager;
import com.example.my_bot.dto.ban.MemberBanStatus;
import com.example.my_bot.entity.BanEntity;
import com.example.my_bot.exception.ban.UserHasNotBannedException;
import com.example.my_bot.exception.command.CannotApplyThisCommandToYourselfException;
import com.example.my_bot.repository.BanRepository;
import jakarta.annotation.Nullable;
import jakarta.transaction.Transactional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

import static com.example.my_bot.enumeration.DefaultRole.isDefaultRole;

@Slf4j
@Service
@RequiredArgsConstructor
public class BanService {
    private final BanRepository banRepository;
    private final MemberService memberService;
    private final CaffeineCacheManager cacheManager;
    private static final long MIN_BAN_PERIOD_IN_SECONDS = 60;
    private static final long MAX_BAN_PERIOD_IN_SECONDS = 777_600_000;


    @Transactional
    public Optional<Instant> createMemberBan(long chatId, long memberId, @Nullable String reason, @Nullable Long periodInSeconds, long fromId){
        if(memberId==fromId){
            throw new CannotApplyThisCommandToYourselfException();
        }
        memberService.checkMemberInteractionAbility(chatId, fromId, memberId,true);
        Instant now = Instant.now();
        Instant unbanAt=null;
        if(periodInSeconds!=null){
            if(periodInSeconds<MIN_BAN_PERIOD_IN_SECONDS){
                periodInSeconds = MIN_BAN_PERIOD_IN_SECONDS;
         }if(periodInSeconds>MAX_BAN_PERIOD_IN_SECONDS){
                periodInSeconds = MAX_BAN_PERIOD_IN_SECONDS;
            }
            unbanAt = now.plusSeconds(periodInSeconds);
        }
        if(reason!=null){
            reason = reason.trim();
            reason = reason.isEmpty()?null:reason;
        }
        BanEntity newBan = banRepository.findByChatIdAndMemberId(chatId, memberId)
                .orElse(new BanEntity());

        newBan.setBannedBy(fromId);
        newBan.setBannedUntil(unbanAt);
        newBan.setReason(reason);
        newBan.setBannedAt(now);
        if(newBan.getId()==null){     // сущность новая
            newBan.setMemberId(memberId);
            newBan.setChatId(chatId);
        }newBan = banRepository.save(newBan);
        putBanToCache(newBan);
        return Optional.ofNullable(unbanAt);

    }
    @Transactional
    public void deleteMemberBan(long chatId, long memberId){
        if(!getMemberBanStatus(chatId,memberId).isBanned()){
            throw new UserHasNotBannedException(memberId);
        }
        cacheManager.getBanCache().invalidate(new ChatIdAndMemberIdKey(chatId, memberId));
        banRepository.deleteByChatIdAndMemberId(chatId, memberId);

    }

    public MemberBanStatus getMemberBanStatus(long chatId, long memberId){
        ChatIdAndMemberIdKey key = new ChatIdAndMemberIdKey(chatId, memberId);
        return cacheManager.getBanCache().asMap().computeIfAbsent(key,k->{
            BanEntity memberBan = banRepository.findByChatIdAndMemberId(chatId, memberId).orElse(null);
            if(memberBan==null||(memberBan.getBannedUntil()!=null&&!memberBan.getBannedUntil().isAfter(Instant.now()))){
                // либо бана никакого нет, либо есть истёкший временный бан
                return new MemberBanStatus(memberId, false, null);
            } return new MemberBanStatus(memberId, true, memberBan.getBannedUntil());

        });
    }

    @Scheduled(fixedRate = 1_800_000)
    protected void deleteExpiredDbBans(){
        banRepository.deleteExpiredBans(Instant.now());
    }

   private void putBanToCache(@NonNull BanEntity banEntity){
       MemberBanStatus banStatus = new MemberBanStatus(banEntity.getMemberId(), true, banEntity.getBannedUntil());
       ChatIdAndMemberIdKey key = new ChatIdAndMemberIdKey(banEntity.getChatId(), banEntity.getMemberId());
       cacheManager.getBanCache().put(key, banStatus);
   }

   public long getMinBanPeriodInSeconds(){
        return MIN_BAN_PERIOD_IN_SECONDS;
   }

   public long getMaxBanPeriodInSeconds(){
        return MAX_BAN_PERIOD_IN_SECONDS;
   }





}
