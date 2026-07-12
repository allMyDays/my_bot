package com.example.my_bot.service;

import com.example.my_bot.dto.member.inactive.InactiveMemberDto;
import com.example.my_bot.dto.member.inactive.InactiveMembersResult;
import com.example.my_bot.dto.member.stat.ChatMembersStatisticResult;
import com.example.my_bot.dto.member.stat.MemberStatisticDto;
import com.example.my_bot.entity.MessageLogEntity;
import com.example.my_bot.exception.message.FindingMessageIntervalOutOfBoundsException;
import com.example.my_bot.exception.message.InactiveMembersStatisticIntervalOutOfBoundsException;
import com.example.my_bot.exception.message.MemberStatisticIntervalOutOfBoundsException;
import com.example.my_bot.exception.role.RoleNotFoundException;
import com.example.my_bot.repository.MessageLogRepository;
import com.example.my_bot.vk.mapping.action.VkAction;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.example.my_bot.utils.ChatUtils.*;

@Slf4j
@Service
public class MessageLogService{

    private final MemberService memberService;
    private final MessageLogRepository messageRepository;
    private final RoleService roleService;
    private final long theMainBotId;

    private final ConcurrentMap<Long, Set<MessageLogEntity>> temporaryMessagesCache = new ConcurrentHashMap<>();

    private final static int INTERVAL_BETWEEN_SAVING_MESSAGES_INTO_DATA_BASE_SEC = 60;

    private final static int INACTIVE_MEMBERS_MIN_PERIOD_SEC = INTERVAL_BETWEEN_SAVING_MESSAGES_INTO_DATA_BASE_SEC;
    private final static int INACTIVE_MEMBERS_MAX_PERIOD_SEC = 315_360_000;

    private final static int STATISTIC_MIN_PERIOD_SEC = INTERVAL_BETWEEN_SAVING_MESSAGES_INTO_DATA_BASE_SEC;
    private final static int STATISTIC_MAX_PERIOD_SEC = 630_720_000;

    private final static long FINDING_MESSAGES_MAX_TIME_PERIOD_SEC = 630_720_000;

    public MessageLogService(MemberService memberService, MessageLogRepository messageRepository, RoleService roleService, @Value("${vk.main-bot.id}") long theMainBotId){
        this.memberService = memberService;
        this.messageRepository = messageRepository;
        this.roleService = roleService;
        this.theMainBotId = theMainBotId;
    }


    public void saveNewMessageLog(long chatId, long fromId, int conversationMessageId, @Nullable VkAction action, @Nullable String text, boolean isForwardedToLogChat){
        if(action!=null) return;
        int symbolsQuantity = text!=null?text.length():0;

        MessageLogEntity newEntity = new MessageLogEntity(chatId, fromId, conversationMessageId, Instant.now(), symbolsQuantity,false);
        newEntity.setForwardedToLogChat(isForwardedToLogChat);
        temporaryMessagesCache.compute(chatId, (key, existingSet) -> {
            if(existingSet==null){
                existingSet = ConcurrentHashMap.newKeySet();
            }
            existingSet.add(newEntity);
            return existingSet;
        });
    }

    @Scheduled(fixedRate = INTERVAL_BETWEEN_SAVING_MESSAGES_INTO_DATA_BASE_SEC * 1_000)
    protected void loadAllChatsMessagesIntoTheDatabase(){
        log.info("Starting log messages batch save");

        Set<MessageLogEntity> collectedMessages = new HashSet<>();

        for(Long chatId: temporaryMessagesCache.keySet()) {
            Set<MessageLogEntity> oldSet = temporaryMessagesCache.replace(chatId, ConcurrentHashMap.newKeySet());
            if(oldSet!=null){
                collectedMessages.addAll(oldSet);
            }
        }
        if(!collectedMessages.isEmpty()){
            messageRepository.saveAll(collectedMessages);
            messageRepository.flush();
        }
    }
    private void loadRequiredChatMessagesIntoTheDatabase(long chatId){

        Set<MessageLogEntity> oldSet = temporaryMessagesCache.replace(chatId, ConcurrentHashMap.newKeySet());

        if(oldSet!=null&&!oldSet.isEmpty()){
            messageRepository.saveAll(oldSet);
            messageRepository.flush();
        }
    }

    public Optional<Long> getMessageOwnerId(long chatId, int conversationMessageId){
        Set<MessageLogEntity> freshChatMessages = temporaryMessagesCache.get(chatId);
        Optional<MessageLogEntity> found=Optional.empty();
        if(freshChatMessages!=null){
                     found = freshChatMessages.stream()
                    .filter(c->c.getConversationMessageId()==conversationMessageId)
                    .findFirst();
        }
        if(found.isEmpty()){
            found = messageRepository.findByChatIdAndConversationMessageId(chatId, conversationMessageId);
        }
        return found.map(MessageLogEntity::getFromId);
    }

    public List<Integer> findLastMessagesForwardedToLogChat(long logChatId, int msgQuantity){
        if(msgQuantity<1) msgQuantity= 1;
        if(msgQuantity>FORWARDED_MESSAGES_MAX_LIMIT) msgQuantity= FORWARDED_MESSAGES_MAX_LIMIT;

        loadRequiredChatMessagesIntoTheDatabase(logChatId);

        Pageable limit = PageRequest.of(0, msgQuantity);
        return messageRepository.findLastNUndeletedMessagesForwardedToLogChat(logChatId, limit);
    }

    public InactiveMembersResult findCurrentInactiveChatMembers(long chatId, long timePeriodSec, boolean sort, @Nullable Integer roleLessThan, @Nullable Integer memberLimit){

        if(timePeriodSec<INACTIVE_MEMBERS_MIN_PERIOD_SEC||timePeriodSec>INACTIVE_MEMBERS_MAX_PERIOD_SEC){
            throw new InactiveMembersStatisticIntervalOutOfBoundsException(INACTIVE_MEMBERS_MIN_PERIOD_SEC, INACTIVE_MEMBERS_MAX_PERIOD_SEC);
        }
        Instant thresholdDate = Instant.now().minusSeconds(timePeriodSec);
        InactiveMembersResult resultToReturn = new InactiveMembersResult();
        List<Long> requiredMembers;

        if(roleLessThan!=null){
            if(!roleService.roleExistsByPriority(chatId, roleLessThan)){
                throw new RoleNotFoundException();
            }
            requiredMembers = memberService.getAllCurrentChatMembersWithRoleLessThanAndFirstAppearanceBeforeThan(chatId, roleLessThan, thresholdDate);
        }else{
            requiredMembers = memberService.getAllCurrentChatMembersWithFirstAppearanceBeforeThan(chatId, thresholdDate);
        }
        Map<Long, MessageLogRepository.MemberLastMessageProjection> lastMessagesMap =
                messageRepository.findLastMessageOfRequiredMembers(chatId, requiredMembers).stream()
                        .collect(Collectors.toMap(m->m.getFromId(), Function.identity()));

        int totalInactiveQuantity=0;

        for(Long userId: requiredMembers){
            var msgProjection = lastMessagesMap.get(userId);
            if(msgProjection!=null&&msgProjection.getLastMessageAt().isAfter(thresholdDate)){
                // участник имеет свежее последнее сообщение
                continue;
            }
            if(memberLimit==null||totalInactiveQuantity<memberLimit){
                resultToReturn.addNewInactiveMember(userId, msgProjection==null?null:msgProjection.getLastMessageAt());// участник достаточно старый и либо писал давно, либо вообще ничего не писал
            }
            totalInactiveQuantity++;
        }
        resultToReturn.setTotalInactiveQuantity(totalInactiveQuantity);
        resultToReturn.setThresholdDate(thresholdDate);
        if(sort){
            resultToReturn.getInactiveMembers().sort(Comparator.comparing(
                    (InactiveMemberDto r) -> r.getLastMessageAt().orElse(null),
                    Comparator.nullsLast(Comparator.reverseOrder())
            ));
        }
        return resultToReturn;
    }


    public ChatMembersStatisticResult getAllChatMembersStatForATimePeriod(long chatId, long timePeriodSec, @Nullable Integer memberLimit){

        Instant end = Instant.now();
        Instant start = end.minusSeconds(timePeriodSec);

        return getAllChatMembersStatForATimePeriod(chatId, start, end, memberLimit);
    }

    public ChatMembersStatisticResult getAllChatMembersStatForATimePeriod(long chatId, @NonNull Instant start, @NonNull Instant end, @Nullable Integer memberLimit){
        ChatMembersStatisticResult result = new ChatMembersStatisticResult();

        if(!start.isBefore(end)){
            throw new MemberStatisticIntervalOutOfBoundsException("Дата, с которой нужно посмотреть статистику, обязана быть раньше чем конечная дата.");
        }
        long periodSec = Duration.between(start, end).toSeconds();
        if(periodSec<STATISTIC_MIN_PERIOD_SEC||periodSec>STATISTIC_MAX_PERIOD_SEC){
            throw new MemberStatisticIntervalOutOfBoundsException(STATISTIC_MIN_PERIOD_SEC, STATISTIC_MAX_PERIOD_SEC);
        }
        List<MessageLogRepository.MemberStatsProjection> membersStatList = messageRepository.getChatMembersStat(chatId, start, end);

        AtomicInteger counter= new AtomicInteger();
        membersStatList.forEach(ms->{
            result.setTotalMessageQuantity(result.getTotalMessageQuantity()+ms.getMessageCount());
            result.setTotalSymbolsQuantity(result.getTotalSymbolsQuantity()+ms.getTotalSymbols());

            if(memberLimit==null||counter.get()<memberLimit){
                result.addMemberStat(
                        new MemberStatisticDto(ms.getFromId(), ms.getMessageCount(), ms.getTotalSymbols())
                );
            }
            counter.incrementAndGet();
        });
        result.setStart(start);
        result.setEnd(end);
        result.setTotalMembersQuantity(membersStatList.size());

        return result;
    }

    public Page<Integer> findNotDeletedMessageIdsOfNotAChatAdminOwner(long chatId, long memberId, long timePeriodSec, int messageLimit, long currentBotId){
        if(timePeriodSec>FINDING_MESSAGES_MAX_TIME_PERIOD_SEC){
            throw new FindingMessageIntervalOutOfBoundsException();
        }
        if(memberId!=-theMainBotId&&memberId!=-currentBotId&&memberService.isChatAdmin(chatId, memberId)) return Page.empty();
        loadRequiredChatMessagesIntoTheDatabase(chatId);

        Instant after = Instant.now().minusSeconds(timePeriodSec<=0?1:timePeriodSec);
        Pageable limit = PageRequest.of(0, messageLimit<=0?1:messageLimit);

        return messageRepository.findFreshNotDeletedMemberMessageIds(chatId, memberId, after, limit);
    }

    public Page<Integer> findNotDeletedMessageIdsOfNotChatAdminOwners(long chatId, long timePeriodSec, int messageLimit, long currentBotId){
        if(timePeriodSec>FINDING_MESSAGES_MAX_TIME_PERIOD_SEC){
            throw new FindingMessageIntervalOutOfBoundsException();
        }
        loadRequiredChatMessagesIntoTheDatabase(chatId);

        Instant after = Instant.now().minusSeconds(timePeriodSec<=0?1:timePeriodSec);
        Pageable limit = PageRequest.of(0, messageLimit<=0?1:messageLimit);

        List<Long> chatAdmins = memberService.getAllChatAdmins(chatId).stream()
                .filter(a->a!=-theMainBotId&&a!=-currentBotId)
                .toList();

        return messageRepository.findFreshNotDeletedMessageIdsOfOwnersNotIn(chatId, chatAdmins, after, limit);
    }

    public void markMessagesAsDeleted(long chatId, @NonNull Set<Integer> conversationMessageIds){
        loadRequiredChatMessagesIntoTheDatabase(chatId);
        messageRepository.markMessagesAsDeleted(chatId, conversationMessageIds);
    }










}
