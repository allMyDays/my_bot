package com.example.my_bot.service;

import com.example.my_bot.dto.member.inactive.InactiveMemberDto;
import com.example.my_bot.dto.member.inactive.InactiveMembersResult;
import com.example.my_bot.dto.member.stat.ChatMembersStatisticResult;
import com.example.my_bot.dto.member.stat.MemberStatisticDto;
import com.example.my_bot.entity.MessageLogEntity;
import com.example.my_bot.exception.member.InactiveMembersIntervalOutOfBoundsException;
import com.example.my_bot.exception.member.MemberStatisticIntervalOutOfBoundsException;
import com.example.my_bot.exception.role.RoleNotFoundException;
import com.example.my_bot.repository.MessageLogRepository;
import com.example.my_bot.vk.VkMessage;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.example.my_bot.utils.ChatUtils.extractConversationId;
import static com.example.my_bot.utils.ChatUtils.isPersonalChat;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageLogService{

    private final MemberService memberService;
    private final MessageLogRepository messageRepository;
    private final RoleService roleService;
    private final ConcurrentMap<Long, Set<MessageLogEntity>> temporaryMessagesCache = new ConcurrentHashMap<>();

    private final static int INTERVAL_BETWEEN_SAVING_MESSAGES_INTO_DATA_BASE_SEC = 60;

    private final static int INACTIVE_MEMBERS_MIN_PERIOD_SEC = INTERVAL_BETWEEN_SAVING_MESSAGES_INTO_DATA_BASE_SEC;
    private final static int INACTIVE_MEMBERS_MAX_PERIOD_SEC = 315_360_000;

    private final static int STATISTIC_MIN_PERIOD_SEC = INTERVAL_BETWEEN_SAVING_MESSAGES_INTO_DATA_BASE_SEC;
    private final static int STATISTIC_MAX_PERIOD_SEC = 630_720_000;


    public void saveNewMessageLog(@NonNull VkMessage message){
        if(isPersonalChat(message.getPeerId())) return;
        if(message.getAction()!=null) return;
        long chatId = extractConversationId(message.getPeerId());
        int symbolsQuantity = message.getText()!=null?message.getText().length():0;

        MessageLogEntity newEntity = new MessageLogEntity(chatId, message.getFromId(), message.getConversationMessageId(), Instant.now(), symbolsQuantity,false);
        temporaryMessagesCache.compute(chatId, (key, existingSet) -> {
            if(existingSet==null){
                existingSet = ConcurrentHashMap.newKeySet();
            }
            existingSet.add(newEntity);
            return existingSet;
        });
    }

    public Optional<Long> getMessageOwnerId(long chatId, int conversationMessageId){
        Set<MessageLogEntity> freshChatMessages = temporaryMessagesCache.get(chatId);
        MessageLogEntity found;
        if(freshChatMessages!=null){
                     found = freshChatMessages.stream()
                    .filter(c->c.getConversationMessageId()==conversationMessageId)
                    .findFirst()
                             .orElse(null);
            if(found!=null) return Optional.of(found.getFromId());
        }
        found = messageRepository.findByChatIdAndConversationMessageId(chatId, conversationMessageId)
                .orElse(null);
        if(found!=null) return Optional.of(found.getFromId());
        return  Optional.empty();
    }

    @Scheduled(fixedRate = INTERVAL_BETWEEN_SAVING_MESSAGES_INTO_DATA_BASE_SEC * 1_000)
    protected void loadMessagesIntoTheDatabase(){
        log.info("Starting log messages batch save");

        Set<MessageLogEntity> collectedMessages = new HashSet<>();

        for(Long chatId : temporaryMessagesCache.keySet()) {
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

    public InactiveMembersResult findCurrentInactiveChatMembers(long chatId, long timePeriodSec, boolean sort, @Nullable Integer roleLessThan, @Nullable Integer memberLimit){

        if(timePeriodSec<INACTIVE_MEMBERS_MIN_PERIOD_SEC||timePeriodSec>INACTIVE_MEMBERS_MAX_PERIOD_SEC){
            throw new InactiveMembersIntervalOutOfBoundsException(INACTIVE_MEMBERS_MIN_PERIOD_SEC, INACTIVE_MEMBERS_MAX_PERIOD_SEC);
        }
        Instant thresholdDate = Instant.now().minusSeconds(timePeriodSec);
        InactiveMembersResult resultToReturn = new InactiveMembersResult();
        List<Long> requiredMembers;

        if(roleLessThan!=null){
            if(!roleService.roleExistsByPriority(chatId, roleLessThan)){
                throw new RoleNotFoundException();
            }
            requiredMembers = memberService.getAllCurrentChatMemberWithRoleLessThanAndFirstAppearanceBeforeThan(chatId, roleLessThan, thresholdDate);
        }else{
            requiredMembers = memberService.getAllCurrentChatMemberWithFirstAppearanceBeforeThan(chatId, thresholdDate);
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


    public ChatMembersStatisticResult getAllMembersStatForRequiredTimePeriod(long chatId, long timePeriodSec, @Nullable Integer memberLimit){

        if(timePeriodSec<STATISTIC_MIN_PERIOD_SEC||timePeriodSec>STATISTIC_MAX_PERIOD_SEC){
            throw new MemberStatisticIntervalOutOfBoundsException(STATISTIC_MIN_PERIOD_SEC, STATISTIC_MAX_PERIOD_SEC);
        }
        ChatMembersStatisticResult result = new ChatMembersStatisticResult();

        Instant end = Instant.now();
        Instant start = end.minusSeconds(timePeriodSec);

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






}
