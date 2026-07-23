package com.example.my_bot.unit.service;

import com.example.my_bot.dto.member.MemberDto;
import com.example.my_bot.dto.warn.CreateWarnResult;
import com.example.my_bot.entity.WarnEntity;
import com.example.my_bot.exception.command.CannotApplyThisCommandToYourselfException;
import com.example.my_bot.exception.member.UserNeverBeenInChatException;
import com.example.my_bot.repository.WarnRepository;
import com.example.my_bot.service.MemberService;
import com.example.my_bot.service.WarnService;
import com.example.my_bot.service.chat.ChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WarnServiceTest {

    @Mock
    private WarnRepository warnRepository;

    @Mock
    private MemberService memberService;

    @Mock
    private ChatService chatService;

    @InjectMocks
    private WarnService warnService;

    private static final long CHAT_ID = 1L;
    private static final long MEMBER_ID = 2L;
    private static final long FROM_ID = 3L;
    private static final String REASON = "Spam";
    private static final long TIME_PERIOD_SEC = 3600L;

    @BeforeEach
    void setUp() {
        lenient().when(memberService.getCachedMemberInfo(anyLong(), anyLong()))
                .thenReturn(Optional.of(mock(MemberDto.class)));

        lenient().doNothing().when(memberService).checkMemberInteractionAbility(anyLong(), anyLong(), anyLong(), anyBoolean());

        lenient().when(chatService.getWarnMaxQuantity(anyLong())).thenReturn(WarnService.DEFAULT_CHAT_WARN_QUANTITY);
    }


    @Test
    void createNewWarn_shouldThrowWhenMemberIsFrom() {
        assertThatThrownBy(() -> warnService.createNewWarn(CHAT_ID, FROM_ID, REASON, TIME_PERIOD_SEC, FROM_ID))
                .isInstanceOf(CannotApplyThisCommandToYourselfException.class);

        verify(memberService, never()).getCachedMemberInfo(anyLong(), anyLong());
        verify(memberService, never()).checkMemberInteractionAbility(anyLong(), anyLong(), anyLong(), anyBoolean());
        verifyNoInteractions(warnRepository);
    }

    @Test
    void createNewWarn_shouldThrowWhenMemberNeverBeenInChat() {
        when(memberService.getCachedMemberInfo(CHAT_ID, MEMBER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> warnService.createNewWarn(CHAT_ID, MEMBER_ID, REASON, TIME_PERIOD_SEC, FROM_ID))
                .isInstanceOf(UserNeverBeenInChatException.class);

        verify(memberService).getCachedMemberInfo(CHAT_ID, MEMBER_ID);
        verify(memberService, never()).checkMemberInteractionAbility(anyLong(), anyLong(), anyLong(), anyBoolean());
        verifyNoInteractions(warnRepository);
    }

    @Test
    void createNewWarn_shouldCallCheckMemberInteractionAbility() throws Exception {
        warnService.createNewWarn(CHAT_ID, MEMBER_ID, REASON, TIME_PERIOD_SEC, FROM_ID);

        verify(memberService).checkMemberInteractionAbility(CHAT_ID, FROM_ID, MEMBER_ID, true);
    }

    @Test
    void createNewWarn_whenWarnLimitReached_shouldDeleteAllAndReturnLimitReached() {
        int activeWarns = WarnService.DEFAULT_CHAT_WARN_QUANTITY-1;
        when(warnRepository.countActiveMemberWarns(eq(CHAT_ID), eq(MEMBER_ID), any(Instant.class)))
                .thenReturn(activeWarns);

        CreateWarnResult result = warnService.createNewWarn(CHAT_ID, MEMBER_ID, REASON, TIME_PERIOD_SEC, FROM_ID);

        assertThat(result.isWarnLimitReached()).isTrue();
        assertThat(result.getNewWarnQuantity()).isEqualTo(activeWarns + 1);
        assertThat(result.getMaxWarnQuantity()).isEqualTo(WarnService.DEFAULT_CHAT_WARN_QUANTITY);
        assertThat(result.getExpiresAt()).isNull();

        verify(warnRepository).deleteAllMemberWarns(CHAT_ID, MEMBER_ID);
        verify(warnRepository, never()).save(any(WarnEntity.class));
    }

    @Test
    void createNewWarn_shouldCreateWarnWithGivenTimePeriod() {
        when(warnRepository.countActiveMemberWarns(eq(CHAT_ID), eq(MEMBER_ID), any(Instant.class))).thenReturn(0);

        CreateWarnResult result = warnService.createNewWarn(CHAT_ID, MEMBER_ID, REASON, TIME_PERIOD_SEC, FROM_ID);

        assertThat(result.isWarnLimitReached()).isFalse();
        assertThat(result.getNewWarnQuantity()).isEqualTo(1);
        assertThat(result.getMaxWarnQuantity()).isEqualTo(WarnService.DEFAULT_CHAT_WARN_QUANTITY);
        assertThat(result.getExpiresAt()).isNotNull();

        ArgumentCaptor<WarnEntity> captor = ArgumentCaptor.forClass(WarnEntity.class);
        verify(warnRepository).save(captor.capture());
        WarnEntity saved = captor.getValue();

        assertThat(saved.getChatId()).isEqualTo(CHAT_ID);
        assertThat(saved.getMemberId()).isEqualTo(MEMBER_ID);
        assertThat(saved.getGivenBy()).isEqualTo(FROM_ID);
        assertThat(saved.getReason()).isEqualTo(REASON);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getExpiresAt()).isAfterOrEqualTo(saved.getCreatedAt().plusSeconds(WarnService.MIN_WARN_TIME_PERIOD_SEC));
        assertThat(saved.getExpiresAt()).isBeforeOrEqualTo(saved.getCreatedAt().plusSeconds(WarnService.MAX_WARN_TIME_PERIOD_SEC));
    }

    @Test
    void createNewWarn_whenTimePeriodNull_shouldCreateWithoutExpiration() {
        when(warnRepository.countActiveMemberWarns(eq(CHAT_ID), eq(MEMBER_ID), any(Instant.class))).thenReturn(0);

        CreateWarnResult result = warnService.createNewWarn(CHAT_ID, MEMBER_ID, REASON, null, FROM_ID);

        assertThat(result.getExpiresAt()).isNull();

        ArgumentCaptor<WarnEntity> captor = ArgumentCaptor.forClass(WarnEntity.class);
        verify(warnRepository).save(captor.capture());
        assertThat(captor.getValue().getExpiresAt()).isNull();
    }

    @Test
    void createNewWarn_whenTimePeriodBelowMin_shouldClampToMin() {
        when(warnRepository.countActiveMemberWarns(eq(CHAT_ID), eq(MEMBER_ID), any(Instant.class))).thenReturn(0);
        long smallPeriod = 10L;

        warnService.createNewWarn(CHAT_ID, MEMBER_ID, REASON, smallPeriod, FROM_ID);

        ArgumentCaptor<WarnEntity> captor = ArgumentCaptor.forClass(WarnEntity.class);
        verify(warnRepository).save(captor.capture());
        Instant expiresAt = captor.getValue().getExpiresAt();
        Instant now = Instant.now();
        long diff = expiresAt.getEpochSecond() - now.getEpochSecond();
        assertThat(diff).isBetween(WarnService.MIN_WARN_TIME_PERIOD_SEC - 2, WarnService.MIN_WARN_TIME_PERIOD_SEC + 2);
    }

    @Test
    void createNewWarn_whenTimePeriodAboveMax_shouldClampToMax() {
        when(warnRepository.countActiveMemberWarns(eq(CHAT_ID), eq(MEMBER_ID), any(Instant.class))).thenReturn(0);
        long hugePeriod = WarnService.MAX_WARN_TIME_PERIOD_SEC + 1000;

        warnService.createNewWarn(CHAT_ID, MEMBER_ID, REASON, hugePeriod, FROM_ID);

        ArgumentCaptor<WarnEntity> captor = ArgumentCaptor.forClass(WarnEntity.class);
        verify(warnRepository).save(captor.capture());
        Instant expiresAt = captor.getValue().getExpiresAt();
        Instant now = Instant.now();
        long diff = expiresAt.getEpochSecond() - now.getEpochSecond();
        assertThat(diff).isBetween(WarnService.MAX_WARN_TIME_PERIOD_SEC - 2, WarnService.MAX_WARN_TIME_PERIOD_SEC + 2);
    }

    @Test
    void createNewWarn_shouldTrimReasonAndSetNullIfEmpty() {
        when(warnRepository.countActiveMemberWarns(eq(CHAT_ID), eq(MEMBER_ID), any(Instant.class))).thenReturn(0);
        String blankReason = "   ";

        warnService.createNewWarn(CHAT_ID, MEMBER_ID, blankReason, TIME_PERIOD_SEC, FROM_ID);

        ArgumentCaptor<WarnEntity> captor = ArgumentCaptor.forClass(WarnEntity.class);
        verify(warnRepository).save(captor.capture());
        assertThat(captor.getValue().getReason()).isNull();
    }





    @Test
    void deleteLastMemberWarn_shouldThrowWhenMemberIsFrom() {
        assertThatThrownBy(() -> warnService.deleteLastMemberWarn(CHAT_ID, FROM_ID, FROM_ID))
                .isInstanceOf(CannotApplyThisCommandToYourselfException.class);

        verify(memberService, never()).checkMemberInteractionAbility(anyLong(), anyLong(), anyLong(), anyBoolean());
        verifyNoInteractions(warnRepository);
    }

    @Test
    void deleteLastMemberWarn_shouldCallCheckMemberInteractionAbility() throws Exception {
        when(warnRepository.deleteLastActiveMemberWarn(anyLong(), anyLong(), any(Instant.class))).thenReturn(1);

        warnService.deleteLastMemberWarn(CHAT_ID, MEMBER_ID, FROM_ID);

        verify(memberService).checkMemberInteractionAbility(CHAT_ID, FROM_ID, MEMBER_ID, true);
    }

    @Test
    void deleteLastMemberWarn_shouldReturnTrueWhenRowsDeleted() {
        when(warnRepository.deleteLastActiveMemberWarn(eq(CHAT_ID), eq(MEMBER_ID), any(Instant.class))).thenReturn(1);

        boolean result = warnService.deleteLastMemberWarn(CHAT_ID, MEMBER_ID, FROM_ID);

        assertThat(result).isTrue();
        verify(warnRepository).deleteLastActiveMemberWarn(eq(CHAT_ID), eq(MEMBER_ID), any(Instant.class));
    }

    @Test
    void deleteLastMemberWarn_shouldReturnFalseWhenNoRowsDeleted() {
        when(warnRepository.deleteLastActiveMemberWarn(eq(CHAT_ID), eq(MEMBER_ID), any(Instant.class))).thenReturn(0);

        boolean result = warnService.deleteLastMemberWarn(CHAT_ID, MEMBER_ID, FROM_ID);

        assertThat(result).isFalse();
        verify(warnRepository).deleteLastActiveMemberWarn(eq(CHAT_ID), eq(MEMBER_ID), any(Instant.class));
    }



    @Test
    void getMemberWarningsSortedInDesc_shouldReturnRepositoryResult() {
        List<WarnEntity> expected = List.of(mock(WarnEntity.class), mock(WarnEntity.class));
        when(warnRepository.findActiveMemberWarningsSortedInDesc(eq(CHAT_ID), eq(MEMBER_ID), any(Instant.class)))
                .thenReturn(expected);

        List<WarnEntity> actual = warnService.getMemberWarningsSortedInDesc(CHAT_ID, MEMBER_ID);

        assertThat(actual).isSameAs(expected);
        verify(warnRepository).findActiveMemberWarningsSortedInDesc(eq(CHAT_ID), eq(MEMBER_ID), any(Instant.class));
    }


    @Test
    void deleteExpiredWarns_shouldCallRepositoryDelete() {
        try {
            java.lang.reflect.Method method = WarnService.class.getDeclaredMethod("deleteExpiredWarns");
            method.setAccessible(true);
            method.invoke(warnService);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        verify(warnRepository).deleteExpiredWarns(any(Instant.class));
    }
}