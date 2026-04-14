package com.example.my_bot.service;

import com.example.my_bot.cache.keys.ChatMemberKey;
import com.example.my_bot.config.CaffeineCacheManager;
import com.example.my_bot.dto.ban.MemberBanStatus;
import com.example.my_bot.dto.member.MemberDto;
import com.example.my_bot.entity.BanEntity;
import com.example.my_bot.entity.MemberEntity;
import com.example.my_bot.exception.ban.BanPeriodOutOfBoundsException;
import com.example.my_bot.exception.command.CannotApplyThisCommandToChatAdminException;
import com.example.my_bot.exception.command.CannotApplyThisCommandToYourselfException;
import com.example.my_bot.repository.BanRepository;
import com.github.benmanes.caffeine.cache.Cache;
import jakarta.annotation.Nullable;
import jakarta.transaction.Transactional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.example.my_bot.enumeration.DefaultRole.isDefaultRole;

@Slf4j
@Service
@RequiredArgsConstructor
public class BanService {
    private final BanRepository banRepository;
    private final MemberService memberService;
    private final CaffeineCacheManager cacheManager;
    private static final int MIN_BAN_PERIOD_IN_SECONDS = 5*60;


    @Transactional
    public Optional<Instant> createMemberBan(long chatId, long memberId, @Nullable String reason, @Nullable Long periodInSeconds, long fromId){
        if(memberId==fromId){
            throw new CannotApplyThisCommandToYourselfException();
        }
        memberService.checkMemberInteractionAbility(chatId, fromId, memberId);
        Instant now = Instant.now();
        Instant unbanAt=null;
        if(periodInSeconds!=null){
            if(periodInSeconds<MIN_BAN_PERIOD_IN_SECONDS){
            throw new BanPeriodOutOfBoundsException(MIN_BAN_PERIOD_IN_SECONDS);
         } unbanAt = now.plusSeconds(periodInSeconds);
        }
        if(reason!=null){
            reason = reason.trim();
            reason = reason.isEmpty()?null:reason;
        }
        BanEntity newBan = banRepository.findByChatIdAndMemberId(chatId, memberId)
                .orElse(new BanEntity());

        newBan.setBannedBy(fromId);
        newBan.setUnbanAt(unbanAt);
        newBan.setReason(reason);
        newBan.setBannedAt(now);
        if(newBan.getId()==null){     // сущность новая
            newBan.setMemberId(memberId);
            newBan.setChatId(chatId);
        }newBan = banRepository.save(newBan);
        putBanToCache(newBan);
        return Optional.ofNullable(unbanAt);

    }

    public MemberBanStatus isMemberBanned(long chatId, long memberId){
        ChatMemberKey key = new ChatMemberKey(chatId, memberId);
        MemberBanStatus memberBanStatus = cacheManager.getBanCache().asMap().computeIfAbsent(key,k->{
            Optional<BanEntity> memberBan = banRepository.findByChatIdAndMemberId(chatId, memberId);
            return memberBan.map(ban -> new MemberBanStatus(memberId, true, ban.getUnbanAt()))
                    .orElseGet(() -> new MemberBanStatus(memberId, false, null));
        });
        Instant now = Instant.now();
        if(memberBanStatus.isBanned()){
            Optional<Instant> bannedUntil = memberBanStatus.getBannedUntil();
            if(bannedUntil.isPresent()&&!bannedUntil.get().isAfter(now)){  // проверка срока временного бана, так как в бд могут быть старые записи
                return new MemberBanStatus(memberId, false, null);
            }
        } return memberBanStatus;
    }

    @Scheduled(fixedRate = 1_800_000)
    protected void deleteExpiredBans(){
        banRepository.deleteExpiredBans(Instant.now());
    }

   private void putBanToCache(@NonNull BanEntity banEntity){
       MemberBanStatus banStatus = new MemberBanStatus(banEntity.getMemberId(), true, banEntity.getUnbanAt());
       ChatMemberKey key = new ChatMemberKey(banEntity.getChatId(), banEntity.getMemberId());
       cacheManager.getBanCache().put(key, banStatus);
   }


}
