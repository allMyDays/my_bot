package com.example.my_bot.unit.service.event;


import com.example.my_bot.annotation.Command;
import com.example.my_bot.command.CommandRegistry;
import com.example.my_bot.config.CaffeineCacheManager;
import com.example.my_bot.dto.RoleDto;
import com.example.my_bot.dto.event.EventDto;
import com.example.my_bot.dto.member.MemberDto;
import com.example.my_bot.entity.EventEntity;

import com.example.my_bot.enumeration.event.ChatEventType;
import com.example.my_bot.enumeration.event.MyEventType;
import com.example.my_bot.enumeration.member.MemberPresenceType;
import com.example.my_bot.exception.command.CannotApplyThisCommandToYourselfException;
import com.example.my_bot.exception.command.CommandInitAnnotationAbsentsException;
import com.example.my_bot.exception.command.UserCommandNotFoundException;
import com.example.my_bot.exception.event.*;
import com.example.my_bot.exception.member.UserNeverBeenInChatException;
import com.example.my_bot.exception.role.RoleAccessDeniedException;
import com.example.my_bot.exception.role.RoleNotFoundException;
import com.example.my_bot.mapper.EventMapper;
import com.example.my_bot.repository.EventRepository;
import com.example.my_bot.resolver.UserInputResolver;
import com.example.my_bot.service.*;
import com.example.my_bot.service.command.CommandAccessService;
import com.example.my_bot.service.event.EventService;
import com.github.benmanes.caffeine.cache.Cache;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static com.example.my_bot.enumeration.event.MyEventType.*;
import static com.example.my_bot.utils.ChatUtils.CHAT_MANAGER_ROLE_PRIORITY;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EventServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private CommandAccessService commandAccessService;

    @Mock
    private MemberService memberService;

    @Mock
    private RoleService roleService;

    @Mock
    private CommandRegistry commandRegistry;

    @Mock
    private CaffeineCacheManager cacheManager;

    @Mock
    private EventMapper eventMapper;

    @Mock
    private UserInputResolver userInputResolver;

    @Mock
    private Cache<Long, ImmutableMap<ChatEventType, ImmutableSet<EventDto>>> eventsCache;

    @InjectMocks
    private EventService eventService;

    private final long chatId = 1L;
    private final long fromId = 100L;
    private final String fullCommand = "/remind test";
    private final String userCommand = "/remind";
    private final String roleName = "admin";
    private final int rolePriority = 50;
    private final long eventId = 10L;
    private final long memberId = 200L;

    @BeforeEach
    void setUp() {
        eventService.setCommandRegistry(commandRegistry);
        given(cacheManager.getEventsCache()).willReturn(eventsCache);
        // Мокаем получение кеша: возвращаем пустую карту по умолчанию
        given(eventsCache.get(anyLong(), any())).willReturn((ImmutableMap.of()));
    }


    @Nested
    class CreateNewEventWithPriorityTest {

        @Test
        void shouldCreateEventSuccessfully() {
            // given
            MyEventType eventType = WORD_FILTER;
            String userArgument = "test";
            boolean delete = false, reply = false, silent = false;

            given(eventService.countChatEvents(chatId)).willReturn(0);
            given(roleService.roleExistsByPriority(chatId, rolePriority)).willReturn(true);
            doNothing().when(roleService).checkRoleInteractionAbility(chatId, rolePriority, fromId);

            mockCommandValidation(true);

            EventEntity savedEvent = new EventEntity();
            savedEvent.setId(eventId);
            given(eventRepository.save(any(EventEntity.class))).willReturn(savedEvent);

            EventEntity result = eventService.createNewEvent(chatId, eventType, rolePriority, userArgument, fullCommand, fromId, delete, reply, silent);

            assertThat(result).isSameAs(savedEvent);
            verify(eventRepository).save(argThat(e ->
                    e.getChatId() == chatId &&
                            e.getType() == eventType &&
                            e.getRolePriority() == rolePriority &&
                            e.getArgument().equals(userArgument) &&
                            e.getFullCommand().equals(fullCommand) &&
                            e.isDelete() == delete &&
                            e.isReply() == reply &&
                            e.isSilent() == silent
            ));
            verify(eventsCache).invalidate(chatId);
        }

        @Test
        void shouldThrowWhenTooManyEvents() {
            EventService spy = spy(eventService);
            doReturn(100).when(spy).countChatEvents(chatId);

            given(roleService.roleExistsByPriority(chatId, rolePriority)).willReturn(true);
            doNothing().when(roleService).checkRoleInteractionAbility(chatId, rolePriority, fromId);
            mockCommandValidation(true);

            assertThatThrownBy(() -> spy.createNewEvent(chatId, WORD_FILTER, rolePriority, "arg", fullCommand, fromId, false, false, false))
                    .isInstanceOf(TooManyEventsException.class);
        }

        @Test
        void shouldThrowWhenSubscriptionEventLimitExceeded() {
            EventService spy = spy(eventService);
            doReturn(5).when(spy).countChatEvents(chatId);

            Set<EventDto> subscriptionEvents = new HashSet<>();
            for (int i = 0; i < 10; i++) {
                subscriptionEvents.add(createEventDto(i, WITH_SUBSCRIPTION, 20, "1", "", 1));
            }
            doReturn(subscriptionEvents).when(spy).getChatEventsWithRequiredTypes(eq(chatId), anySet());

            given(roleService.roleExistsByPriority(chatId, rolePriority)).willReturn(true);
            doNothing().when(roleService).checkRoleInteractionAbility(chatId, rolePriority, fromId);
            mockCommandValidation(true);

            given(userInputResolver.getMemberIdByStringInput(anyLong(), anyString()))
                    .willReturn(Optional.of(-123L));

            assertThatThrownBy(() -> spy.createNewEvent(chatId, WITH_SUBSCRIPTION, rolePriority, "vk.com/club1", fullCommand, fromId, false, false, false))
                    .isInstanceOf(TooManyEventsException.class)
                    .hasMessageContaining("подпиской на сообщества");
        }

        @Test
        void shouldThrowWhenRoleNotFound() {
            given(eventService.countChatEvents(chatId)).willReturn(0);
            given(roleService.roleExistsByPriority(chatId, rolePriority)).willReturn(false);
            assertThatThrownBy(() -> eventService.createNewEvent(chatId, WORD_FILTER, rolePriority, "arg", fullCommand, fromId, false, false, false))
                    .isInstanceOf(RoleNotFoundException.class);
        }

        @Test
        void shouldThrowWhenRoleInteractionNotAllowed() {
            given(eventService.countChatEvents(chatId)).willReturn(0);
            given(roleService.roleExistsByPriority(chatId, rolePriority)).willReturn(true);
            doThrow(new RoleAccessDeniedException()).when(roleService).checkRoleInteractionAbility(chatId, rolePriority, fromId);
            assertThatThrownBy(() -> eventService.createNewEvent(chatId, WORD_FILTER, rolePriority, "arg", fullCommand, fromId, false, false, false))
                    .isInstanceOf(RoleAccessDeniedException.class);
        }

        @Test
        void shouldThrowWhenCommandValidationFails() {
            given(eventService.countChatEvents(chatId)).willReturn(0);
            given(roleService.roleExistsByPriority(chatId, rolePriority)).willReturn(true);
            doNothing().when(roleService).checkRoleInteractionAbility(chatId, rolePriority, fromId);

            given(commandRegistry.getCommandAnnotation(userCommand)).willReturn(Optional.empty());
            assertThatThrownBy(() -> eventService.createNewEvent(chatId, WORD_FILTER, rolePriority, "arg", fullCommand, fromId, false, false, false))
                    .isInstanceOf(UserCommandNotFoundException.class);
        }

        @Test
        void shouldThrowWhenFullCommandNullButRequired() {
            given(eventService.countChatEvents(chatId)).willReturn(0);
            given(roleService.roleExistsByPriority(chatId, rolePriority)).willReturn(true);
            doNothing().when(roleService).checkRoleInteractionAbility(chatId, rolePriority, fromId);

            assertThatThrownBy(() -> eventService.createNewEvent(chatId, CHAT_INVITE_LINK, rolePriority, null, null, fromId, false, false, false))
                    .isInstanceOf(EventCommandCannotBeNullForThisCaseException.class);
        }

   @Nested
   class CreateNewEventWithRoleNameTest {

       @Test
       void shouldCreateEventWithRoleNameSuccessfully() {
           given(roleService.getRoleByNameIgnoreCase(chatId, roleName)).willReturn(Optional.of(new RoleDto(roleName, rolePriority)));
           EventService spy = spy(eventService);
           doReturn(new EventEntity()).when(spy).createNewEvent(eq(chatId), any(MyEventType.class), eq(rolePriority), any(), any(), eq(fromId), anyBoolean(), anyBoolean(), anyBoolean());

           spy.createNewEvent(chatId, WORD_FILTER, roleName, "arg", fullCommand, fromId, false, false, false);

           verify(spy).createNewEvent(chatId, WORD_FILTER, rolePriority, "arg", fullCommand, fromId, false, false, false);
       }

       @Test
       void shouldTreatCommandRoleNameAsChatManagerPriority() {
           EventService spy = spy(eventService);
           doReturn(new EventEntity()).when(spy).createNewEvent(eq(chatId), any(MyEventType.class), eq(CHAT_MANAGER_ROLE_PRIORITY), any(), any(), eq(fromId), anyBoolean(), anyBoolean(), anyBoolean());

           spy.createNewEvent(chatId, WORD_FILTER, "команда", "arg", fullCommand, fromId, false, false, false);

           verify(spy).createNewEvent(chatId, WORD_FILTER, CHAT_MANAGER_ROLE_PRIORITY, "arg", fullCommand, fromId, false, false, false);
       }

       @Test
       void shouldThrowWhenRoleNameNotFound() {
           given(roleService.getRoleByNameIgnoreCase(chatId, roleName)).willReturn(Optional.empty());
           assertThatThrownBy(() -> eventService.createNewEvent(chatId, WORD_FILTER, roleName, "arg", fullCommand, fromId, false, false, false))
                   .isInstanceOf(RoleNotFoundException.class);
       }
   }


        @Test
        void shouldReturnSortedEvents() {
            EventDto dto1 = createEventDto(1, WORD_FILTER, 0, "", "", 1);
            EventDto dto2 = createEventDto(3, WORD_FILTER, 0, "", "", 1);
            EventDto dto3 = createEventDto(2, INVITE_ANOTHER, 0, "", "", 1);

            ImmutableSet<EventDto> set1 = ImmutableSet.of(dto3, dto1);
            ImmutableSet<EventDto> set2 = ImmutableSet.of(dto2);

            ImmutableMap<ChatEventType, ImmutableSet<EventDto>> eventMap = ImmutableMap.of(
                    ChatEventType.TEXT, set1,
                    ChatEventType.ACTION, set2
            );
            given(eventsCache.get(eq(chatId), any())).willReturn(eventMap);

            List<EventDto> result = eventService.getEventsSortedByIdInIncreasingOrder(chatId);

            assertThat(result).containsExactly(dto1, dto3, dto2);
        }

   @Nested
   class SetAETimePeriodAndMaxUsageTest {

       @Test
       void shouldSetAEParametersSuccessfully() {
           EventEntity event = new EventEntity();
           event.setId(eventId);
           event.setChatId(chatId);
           event.setType(WORD_FILTER);
           event.setRolePriority(rolePriority);
           event.setAEMaxUsage(null);

           given(eventRepository.findById(eventId)).willReturn(Optional.of(event));

           doNothing().when(roleService).checkRoleInteractionAbility(chatId, rolePriority, fromId);

           long period = 60;
           int maxUsage = 5;

           EventEntity result = eventService.setAETimePeriodAndMaxUsage(eventId, period, maxUsage, fromId);

           assertThat(result).isSameAs(event);
           assertThat(event.getAEPeriodSec()).isEqualTo((int) period);
           assertThat(event.getAEMaxUsage()).isEqualTo(maxUsage);

           verify(eventsCache).invalidate(chatId);
       }


       @Test
       void shouldThrowWhenEventNotFound() {
           given(eventRepository.findById(eventId)).willReturn(Optional.empty());
           assertThatThrownBy(() -> eventService.setAETimePeriodAndMaxUsage(eventId, 60, 5, fromId))
                   .isInstanceOf(EventNotFoundException.class);
       }

       @Test
       void shouldThrowWhenCommandEvent() {
           EventEntity event = new EventEntity();
           event.setRolePriority(CHAT_MANAGER_ROLE_PRIORITY);
           given(eventRepository.findById(eventId)).willReturn(Optional.of(event));
           assertThatThrownBy(() -> eventService.setAETimePeriodAndMaxUsage(eventId, 60, 5, fromId))
                   .isInstanceOf(CannotApplyThisFunctionToCommandEventException.class);
       }

       @Test
       void shouldThrowWhenAlreadyAdvanced() {
           EventEntity event = new EventEntity();
           event.setRolePriority(rolePriority);
           event.setAEMaxUsage(3);
           given(eventRepository.findById(eventId)).willReturn(Optional.of(event));
           assertThatThrownBy(() -> eventService.setAETimePeriodAndMaxUsage(eventId, 60, 5, fromId))
                   .isInstanceOf(CurrentEventAlreadyAdvancedException.class);
       }

       @Test
       void shouldThrowWhenEventCannotBeAdvanced() {
           EventEntity event = new EventEntity();
           event.setChatId(chatId);
           event.setRolePriority(rolePriority);
           event.setType(WITH_SUBSCRIPTION);
           given(eventRepository.findById(eventId)).willReturn(Optional.of(event));
           assertThatThrownBy(() -> eventService.setAETimePeriodAndMaxUsage(eventId, 60, 5, fromId))
                   .isInstanceOf(CurrentEventCannotBeAdvancedException.class);
       }

       @Test
       void shouldThrowWhenPeriodOutOfRange() {
           EventEntity event = new EventEntity();
           event.setChatId(2l);
           event.setRolePriority(rolePriority);
           event.setType( WORD_FILTER);
           given(eventRepository.findById(eventId)).willReturn(Optional.of(event));
           assertThatThrownBy(() -> eventService.setAETimePeriodAndMaxUsage(eventId, 5, 5, fromId))
                   .isInstanceOf(IncorrectEventArgumentException.class)
                   .hasMessageContaining("Временной период");
       }

       @Test
       void shouldThrowWhenMaxUsageOutOfRange() {
           EventEntity event = new EventEntity();
           event.setRolePriority(rolePriority);
           event.setType( WORD_FILTER);
           event.setChatId(2L);

           given(eventRepository.findById(eventId)).willReturn(Optional.of(event));
           assertThatThrownBy(() -> eventService.setAETimePeriodAndMaxUsage(eventId, 60, 1, fromId))
                   .isInstanceOf(IncorrectEventArgumentException.class)
                   .hasMessageContaining("лимит действия");
       }
   }


   @Nested
   class SetDailyWorkTimeTest {

       @Test
       void shouldSetDailyWorkTimeSuccessfully() {
           EventEntity event = new EventEntity();
           event.setId(eventId);
           event.setChatId(chatId);
           event.setRolePriority(rolePriority);
           given(eventRepository.findById(eventId)).willReturn(Optional.of(event));
           doNothing().when(roleService).checkRoleInteractionAbility(chatId, rolePriority, fromId);

           LocalTime start = LocalTime.of(8, 0);
           LocalTime end = LocalTime.of(20, 0);

           EventEntity result = eventService.setDailyWorkTime(eventId, start, end, fromId);

           assertThat(result).isSameAs(event);
           assertThat(event.getStartDayTime()).isEqualTo(start);
           assertThat(event.getEndDayTime()).isEqualTo(end);
           verify(eventsCache).invalidate(chatId);
       }

       @Test
       void shouldThrowWhenEventNotFound() {
           given(eventRepository.findById(eventId)).willReturn(Optional.empty());
           assertThatThrownBy(() -> eventService.setDailyWorkTime(eventId, LocalTime.now(), LocalTime.now().plusHours(1), fromId))
                   .isInstanceOf(EventNotFoundException.class);
       }

       @Test
       void shouldThrowWhenCommandEvent() {
           EventEntity event = new EventEntity();
           event.setRolePriority(CHAT_MANAGER_ROLE_PRIORITY);
           given(eventRepository.findById(eventId)).willReturn(Optional.of(event));
           assertThatThrownBy(() -> eventService.setDailyWorkTime(eventId, LocalTime.now(), LocalTime.now().plusHours(1), fromId))
                   .isInstanceOf(CannotApplyThisFunctionToCommandEventException.class);
       }

       @Test
       void shouldThrowWhenDifferenceTooSmall() {
           EventEntity event = new EventEntity();
           event.setRolePriority(rolePriority);
           event.setChatId(1l);

           given(eventRepository.findById(eventId)).willReturn(Optional.of(event));
           LocalTime start = LocalTime.of(8, 0);
           LocalTime end = LocalTime.of(8, 10); // 10 минут < 30 минут
           assertThatThrownBy(() -> eventService.setDailyWorkTime(eventId, start, end, fromId))
                   .isInstanceOf(IncorrectEventArgumentException.class)
                   .hasMessageContaining("минимум");
       }
   }

   @Test
   void shouldRemoveDailyWorkTime() {
       EventEntity event = new EventEntity();
       event.setId(eventId);
       event.setChatId(chatId);
       event.setRolePriority(rolePriority);
       event.setStartDayTime(LocalTime.of(8, 0));
       event.setEndDayTime(LocalTime.of(20, 0));
       given(eventRepository.findById(eventId)).willReturn(Optional.of(event));
       doNothing().when(roleService).checkRoleInteractionAbility(chatId, rolePriority, fromId);

       EventEntity result = eventService.removeDailyWorkTime(eventId, fromId);

       assertThat(result).isSameAs(event);
       assertThat(event.getStartDayTime()).isNull();
       assertThat(event.getEndDayTime()).isNull();
       verify(eventsCache).invalidate(chatId);
   }


   @Nested
   class SetCDTimePeriodTest {

       @Test
       void shouldSetCooldownSuccessfully() {
           EventEntity event = new EventEntity();
           event.setId(eventId);
           event.setChatId(chatId);
           event.setRolePriority(rolePriority);
           event.setCDPeriodSec(null);
           given(eventRepository.findById(eventId)).willReturn(Optional.of(event));
           doNothing().when(roleService).checkRoleInteractionAbility(chatId, rolePriority, fromId);

           long period = 600; // 10 минут
           EventEntity result = eventService.setCDTimePeriod(eventId, period, fromId);

           assertThat(result).isSameAs(event);
           assertThat(event.getCDPeriodSec()).isEqualTo((int)period);
           verify(eventsCache).invalidate(chatId);
       }

       @Test
       void shouldSetZeroPeriodWhenNegative() {
           EventEntity event = new EventEntity();
           event.setChatId(chatId);
           event.setRolePriority(rolePriority);
           given(eventRepository.findById(eventId)).willReturn(Optional.of(event));
           doNothing().when(roleService).checkRoleInteractionAbility(anyLong(), anyInt(), anyLong());

           eventService.setCDTimePeriod(eventId, -10, fromId);

           assertThat(event.getCDPeriodSec()).isZero();
       }

       @Test
       void shouldThrowWhenPeriodTooLarge() {
           EventEntity event = new EventEntity();
           event.setChatId(chatId);
           event.setRolePriority(rolePriority);

           given(eventRepository.findById(eventId)).willReturn(Optional.of(event));
           assertThatThrownBy(() -> eventService.setCDTimePeriod(eventId, 4000, fromId))
                   .isInstanceOf(IncorrectEventArgumentException.class)
                   .hasMessageContaining("Максимальный период");
       }

       @Test
       void shouldThrowWhenAlreadyHasCooldown() {
           EventEntity event = new EventEntity();
           event.setRolePriority(rolePriority);
           event.setCDPeriodSec(100);
           event.setChatId(chatId);

           given(eventRepository.findById(eventId)).willReturn(Optional.of(event));
           assertThatThrownBy(() -> eventService.setCDTimePeriod(eventId, 200, fromId))
                   .isInstanceOf(CurrentEventAlreadyHasCooldownException.class);
       }
   }


   @Nested
   class SetNewMembersTimePeriodTest {

       @Test
       void shouldSetNewMembersPeriodSuccessfully() {
           EventEntity event = new EventEntity();
           event.setId(eventId);
           event.setChatId(chatId);
           event.setRolePriority(rolePriority);
           event.setMemberToTrigger(null);
           given(eventRepository.findById(eventId)).willReturn(Optional.of(event));
           doNothing().when(roleService).checkRoleInteractionAbility(chatId, rolePriority, fromId);

           long period = 3600; // 1 час
           EventEntity result = eventService.setNewMembersTimePeriod(eventId, period, fromId);

           assertThat(result).isSameAs(event);
           assertThat(event.getNewMembersPeriodSec()).isEqualTo((int)period);
           verify(eventsCache).invalidate(chatId);
       }

       @Test
       void shouldSetZeroWhenNegative() {
           EventEntity event = new EventEntity();
           event.setRolePriority(rolePriority);
           event.setChatId(chatId);
           given(eventRepository.findById(eventId)).willReturn(Optional.of(event));
           doNothing().when(roleService).checkRoleInteractionAbility(anyLong(), anyInt(), anyLong());

           eventService.setNewMembersTimePeriod(eventId, -5, fromId);
           assertThat(event.getNewMembersPeriodSec()).isZero();
       }

       @Test
       void shouldThrowWhenPeriodTooLarge() {
           EventEntity event = new EventEntity();
           event.setRolePriority(rolePriority);
           event.setChatId(chatId);

           given(eventRepository.findById(eventId)).willReturn(Optional.of(event));
           assertThatThrownBy(() -> eventService.setNewMembersTimePeriod(eventId, 900000, fromId))
                   .isInstanceOf(IncorrectEventArgumentException.class)
                   .hasMessageContaining("Максимальный период");
       }

       @Test
       void shouldThrowWhenPersonalEvent() {
           EventEntity event = new EventEntity();
           event.setMemberToTrigger(memberId);
           event.setChatId(chatId);

           given(eventRepository.findById(eventId)).willReturn(Optional.of(event));
           assertThatThrownBy(() -> eventService.setNewMembersTimePeriod(eventId, 60, fromId))
                   .isInstanceOf(CannotApplyThisFunctionToPersonalEventException.class);
       }
   }


   @Test
   void shouldRemoveNewMembersTimePeriod() {
       EventEntity event = new EventEntity();
       event.setId(eventId);
       event.setChatId(chatId);
       event.setRolePriority(rolePriority);
       event.setNewMembersPeriodSec(100);
       given(eventRepository.findById(eventId)).willReturn(Optional.of(event));
       doNothing().when(roleService).checkRoleInteractionAbility(chatId, rolePriority, fromId);

       EventEntity result = eventService.removeNewMembersTimePeriod(eventId, fromId);

       assertThat(result).isSameAs(event);
       assertThat(event.getNewMembersPeriodSec()).isNull();
       verify(eventsCache).invalidate(chatId);
   }

   @Nested
   class AddMemberToExceptionalTest {

       @Test
       void shouldAddMemberSuccessfully() {
           EventEntity event = new EventEntity();
           event.setId(eventId);
           event.setChatId(chatId);
           event.setRolePriority(rolePriority);
           event.setMemberToTrigger(null);
           event.setExceptionalMembers(new HashSet<>());
           given(eventRepository.findById(eventId)).willReturn(Optional.of(event));
           doNothing().when(roleService).checkRoleInteractionAbility(chatId, rolePriority, fromId);
           doNothing().when(memberService).checkMemberInteractionAbility(chatId, fromId, memberId, true);
           MemberDto memberDto = new MemberDto(memberId, 30, null, false, MemberPresenceType.IN_CHAT, Instant.now(), false);
           given(memberService.getCachedMemberInfo(chatId, memberId)).willReturn(Optional.of(memberDto));

           EventEntity result = eventService.addMemberToExceptional(eventId, memberId, fromId);

           assertThat(result).isSameAs(event);
           assertThat(event.getExceptionalMembers()).contains(memberId);
           verify(eventsCache).invalidate(chatId);
       }

       @Test
       void shouldThrowWhenEventNotFound() {
           given(eventRepository.findById(eventId)).willReturn(Optional.empty());
           assertThatThrownBy(() -> eventService.addMemberToExceptional(eventId, memberId, fromId))
                   .isInstanceOf(EventNotFoundException.class);
       }

       @Test
       void shouldThrowWhenPersonalEvent() {
           EventEntity event = new EventEntity();
           event.setMemberToTrigger(memberId);
           given(eventRepository.findById(eventId)).willReturn(Optional.of(event));
           assertThatThrownBy(() -> eventService.addMemberToExceptional(eventId, memberId, fromId))
                   .isInstanceOf(CannotApplyThisFunctionToPersonalEventException.class);
       }

       @Test
       void shouldThrowWhenExceptionalListFull() {
           EventEntity event = new EventEntity();
           event.setExceptionalMembers(new HashSet<>(Set.of(1L,2L,3L,4L,5L,6L,7L,8L,9L,10L,
                   11L,12L,13L,14L,15L,16L,17L,18L,19L,20L))); // 20 элементов
           given(eventRepository.findById(eventId)).willReturn(Optional.of(event));
           assertThatThrownBy(() -> eventService.addMemberToExceptional(eventId, memberId, fromId))
                   .isInstanceOf(TooManyExceptionalMembersException.class);
       }

       @Test
       void shouldThrowWhenMemberAlreadyInExceptional() {
           EventEntity event = new EventEntity();
           event.setChatId(chatId);
           event.setRolePriority(rolePriority);
           event.setExceptionalMembers(new HashSet<>(Set.of(memberId)));
           given(eventRepository.findById(eventId)).willReturn(Optional.of(event));
           assertThatThrownBy(() -> eventService.addMemberToExceptional(eventId, memberId, fromId))
                   .isInstanceOf(IncorrectEventArgumentException.class)
                   .hasMessageContaining("уже находится в исключении");
       }

       @Test
       void shouldThrowWhenAddingSelf() {
           EventEntity event = new EventEntity();
           event.setRolePriority(rolePriority);
           event.setExceptionalMembers(new HashSet<>());
           event.setChatId(chatId);
           given(eventRepository.findById(eventId)).willReturn(Optional.of(event));
           assertThatThrownBy(() -> eventService.addMemberToExceptional(eventId, fromId, fromId))
                   .isInstanceOf(CannotApplyThisCommandToYourselfException.class);
       }

       @Test
       void shouldThrowWhenEventDoesNotReactToMember() {
           EventEntity event = new EventEntity();
           event.setId(eventId);
           event.setChatId(chatId);
           event.setRolePriority(rolePriority);
           event.setExceptionalMembers(new HashSet<>());
           given(eventRepository.findById(eventId)).willReturn(Optional.of(event));
           doNothing().when(roleService).checkRoleInteractionAbility(chatId, rolePriority, fromId);
           doNothing().when(memberService).checkMemberInteractionAbility(chatId, fromId, memberId, true);
           MemberDto memberDto = new MemberDto(memberId, 60, null, false, MemberPresenceType.IN_CHAT, Instant.now(), false);
           given(memberService.getCachedMemberInfo(chatId, memberId)).willReturn(Optional.of(memberDto));

           assertThatThrownBy(() -> eventService.addMemberToExceptional(eventId, memberId, fromId))
                   .isInstanceOf(EventDoesNotReactToThisMemberException.class);
       }
   }

   @Test
   void shouldRemoveMemberFromExceptional() {
       EventEntity event = new EventEntity();
       event.setId(eventId);
       event.setChatId(chatId);
       event.setRolePriority(rolePriority);
       event.setExceptionalMembers(new HashSet<>(Set.of(memberId)));
       given(eventRepository.findById(eventId)).willReturn(Optional.of(event));
       doNothing().when(roleService).checkRoleInteractionAbility(chatId, rolePriority, fromId);
       doNothing().when(memberService).checkMemberInteractionAbility(chatId, fromId, memberId, true);

       EventEntity result = eventService.removeMemberFromExceptional(eventId, memberId, fromId);

       assertThat(result).isSameAs(event);
       assertThat(event.getExceptionalMembers()).doesNotContain(memberId);
       verify(eventsCache).invalidate(chatId);
   }

   @Test
   void shouldThrowWhenMemberNotInExceptional() {
       EventEntity event = new EventEntity();
       event.setChatId(chatId);
       event.setRolePriority(rolePriority);
       event.setExceptionalMembers(new HashSet<>());
       given(eventRepository.findById(eventId)).willReturn(Optional.of(event));
       assertThatThrownBy(() -> eventService.removeMemberFromExceptional(eventId, memberId, fromId))
               .isInstanceOf(IncorrectEventArgumentException.class)
               .hasMessageContaining("не находится в исключении");
   }


   @Nested
   class SetMemberToTriggerTest {

       @Test
       void shouldSetMemberToTriggerSuccessfully() {
           EventEntity event = new EventEntity();
           event.setId(eventId);
           event.setChatId(chatId);
           event.setRolePriority(rolePriority);
           event.setMemberToTrigger(null);
           event.setNewMembersPeriodSec(100);
           event.setExceptionalMembers(new HashSet<>(Set.of(1L)));
           given(eventRepository.findById(eventId)).willReturn(Optional.of(event));
           doNothing().when(roleService).checkRoleInteractionAbility(chatId, rolePriority, fromId);
           doNothing().when(memberService).checkMemberInteractionAbility(chatId, fromId, memberId, true);
           given(memberService.getCachedMemberInfo(chatId, memberId)).willReturn(Optional.of(
                   new MemberDto(memberId, 30, null, false, MemberPresenceType.IN_CHAT, Instant.now(), false)
           ));

           EventEntity result = eventService.setMemberToTrigger(eventId, memberId, fromId);

           assertThat(result).isSameAs(event);
           assertThat(event.getMemberToTrigger()).isEqualTo(memberId);
           assertThat(event.getRolePriority()).isNull();
           assertThat(event.getNewMembersPeriodSec()).isNull();
           assertThat(event.getExceptionalMembers()).isEmpty();
           verify(eventsCache).invalidate(chatId);
       }

       @Test
       void shouldThrowWhenAddingSelf() {
           EventEntity event = new EventEntity();
           given(eventRepository.findById(eventId)).willReturn(Optional.of(event));
           assertThatThrownBy(() -> eventService.setMemberToTrigger(eventId, fromId, fromId))
                   .isInstanceOf(CannotApplyThisCommandToYourselfException.class);
       }

       @Test
       void shouldThrowWhenMemberNeverInChat() {
           EventEntity event = new EventEntity();
           event.setId(eventId);
           event.setChatId(chatId);
           event.setRolePriority(rolePriority);
           given(eventRepository.findById(eventId)).willReturn(Optional.of(event));
           doNothing().when(memberService).checkMemberInteractionAbility(chatId, fromId, memberId, true);
           given(memberService.getCachedMemberInfo(chatId, memberId)).willReturn(Optional.empty());
           assertThatThrownBy(() -> eventService.setMemberToTrigger(eventId, memberId, fromId))
                   .isInstanceOf(UserNeverBeenInChatException.class);
       }
   }

   @Nested
   class SetNewRoleByPriorityTest {

       @Test
       void shouldSetNewRoleByPrioritySuccessfully() {
           EventEntity event = new EventEntity();
           event.setId(eventId);
           event.setChatId(chatId);
           event.setRolePriority(rolePriority);
           event.setMemberToTrigger(memberId);
           given(eventRepository.findById(eventId)).willReturn(Optional.of(event));
           given(roleService.roleExistsByPriority(chatId, 60)).willReturn(true);
           doNothing().when(roleService).checkRoleInteractionAbility(chatId, 60, fromId);
           doNothing().when(roleService).checkRoleInteractionAbility(chatId, rolePriority, fromId); // для авторизации

           EventEntity result = eventService.setNewRole(eventId, 60, fromId);

           assertThat(result).isSameAs(event);
           assertThat(event.getRolePriority()).isEqualTo(60);
           assertThat(event.getMemberToTrigger()).isNull();
           verify(eventsCache).invalidate(chatId);
       }

       @Test
       void shouldThrowWhenRoleNotFound() {
           EventEntity event = new EventEntity();
           event.setId(eventId);
           event.setChatId(chatId);
           event.setRolePriority(rolePriority);
           given(eventRepository.findById(eventId)).willReturn(Optional.of(event));
           given(roleService.roleExistsByPriority(chatId, 60)).willReturn(false);
           assertThatThrownBy(() -> eventService.setNewRole(eventId, 60, fromId))
                   .isInstanceOf(RoleNotFoundException.class);
       }
   }


   @Nested
   class SetNewRoleByNameTest {

       @Test
       void shouldSetNewRoleByNameSuccessfully() {
           EventEntity event = new EventEntity();
           event.setId(eventId);
           event.setChatId(chatId);
           event.setRolePriority(rolePriority);
           event.setMemberToTrigger(memberId);
           given(eventRepository.findById(eventId)).willReturn(Optional.of(event));
           RoleDto newRole = new RoleDto("moderator", 60);
           given(roleService.getRoleByNameIgnoreCase(chatId, "moderator")).willReturn(Optional.of(newRole));
           doNothing().when(roleService).checkRoleInteractionAbility(chatId, 60, fromId);

           EventEntity result = eventService.setNewRole(eventId, "moderator", fromId);

           assertThat(result).isSameAs(event);
           assertThat(event.getRolePriority()).isEqualTo(60);
           assertThat(event.getMemberToTrigger()).isNull();
           verify(eventsCache).invalidate(chatId);
       }

       @Test
       void shouldThrowWhenCommandEvent() {
           EventEntity event = new EventEntity();
           event.setRolePriority(CHAT_MANAGER_ROLE_PRIORITY);
           given(eventRepository.findById(eventId)).willReturn(Optional.of(event));
           assertThatThrownBy(() -> eventService.setNewRole(eventId, "moderator", fromId))
                   .isInstanceOf(CannotApplyThisFunctionToCommandEventException.class);
       }
   }

   @Nested
   class SetNewCommandTest {

       @Test
       void shouldSetNewCommandSuccessfully() {
           EventEntity event = new EventEntity();
           event.setId(eventId);
           event.setChatId(chatId);
           event.setRolePriority(rolePriority);
           event.setFullCommand("old");
           given(eventRepository.findById(eventId)).willReturn(Optional.of(event));
           doNothing().when(roleService).checkRoleInteractionAbility(chatId, rolePriority, fromId);

           String newUserCommand = "/newcmd";
           Command mockAnnotation = mock(Command.class);
           given(mockAnnotation.eventable()).willReturn(true);
           given(mockAnnotation.mainCommandName()).willReturn(newUserCommand);
           given(commandRegistry.getCommandAnnotation(newUserCommand)).willReturn(Optional.of(mockAnnotation));
           given(memberService.getMemberRolePriority(chatId, fromId)).willReturn(30);
           given(commandAccessService.checkCommandAuthorization(chatId, newUserCommand, 30, fromId)).willReturn(true);

           String newCommand = "/newcmd arg";
           EventEntity result = eventService.setNewCommand(eventId, newCommand, fromId);

           assertThat(result).isSameAs(event);
           assertThat(event.getFullCommand()).isEqualTo("/newcmd arg");
           verify(eventsCache).invalidate(chatId);
       }

       @Test
       void shouldThrowWhenCommandValidationFails() {
           EventEntity event = new EventEntity();
           event.setId(eventId);
           event.setChatId(chatId);
           event.setRolePriority(rolePriority);
           given(eventRepository.findById(eventId)).willReturn(Optional.of(event));
           doNothing().when(roleService).checkRoleInteractionAbility(chatId, rolePriority, fromId);
           given(commandRegistry.getCommandAnnotation(userCommand)).willReturn(Optional.empty());
           assertThatThrownBy(() -> eventService.setNewCommand(eventId, fullCommand, fromId))
                   .isInstanceOf(UserCommandNotFoundException.class);
       }
   }

   @Test
   void shouldCountChatEvents() {
       ImmutableMap<ChatEventType, ImmutableSet<EventDto>> map = ImmutableMap.of(
               ChatEventType.TEXT, ImmutableSet.of(
                       createEventDto(1, WORD_FILTER, 0, "", "", 1),
                       createEventDto(2, WORD_FILTER, 0, "", "", 1)),
               ChatEventType.ACTION, ImmutableSet.of(createEventDto(3, WORD_FILTER, 0, "", "", 1))
       );
       given(eventsCache.get(eq(chatId), any())).willReturn(map);
       int count = eventService.countChatEvents(chatId);
       assertThat(count).isEqualTo(3);
   }

        @Test
        void shouldDeleteEvent() {
            EventEntity event = new EventEntity();
            event.setId(eventId);
            event.setChatId(chatId);
            event.setRolePriority(rolePriority);
            given(eventRepository.findById(eventId)).willReturn(Optional.of(event));
            doNothing().when(roleService).checkRoleInteractionAbility(chatId, rolePriority, fromId);

            eventService.deleteEventById(eventId, fromId);

            ArgumentCaptor<Long> captor = ArgumentCaptor.forClass(Long.class);
            verify(eventRepository).deleteById(captor.capture());
            assertThat(captor.getValue()).isEqualTo(eventId);
            verify(eventsCache).invalidate(chatId);
        }


   @Test
   void shouldGetCachedChatEventsAndPopulateCache() {
       List<EventEntity> entities = List.of(
               new EventEntity(chatId, WORD_FILTER, rolePriority, null, "arg1", fromId, "cmd1", false, false, false),
               new EventEntity(chatId, INVITE_ANOTHER, rolePriority, null, null, fromId, null, true, false, false)
       );
       given(eventRepository.findByChatId(chatId)).willReturn(entities);

       EventDto dto1 = createEventDto(1, WITH_SUBSCRIPTION, rolePriority, "", "", 1);
       EventDto dto2 = createEventDto(1, WITH_SUBSCRIPTION, rolePriority, "", "", 1);
       given(eventMapper.toEventDto(entities.get(0))).willReturn(dto1);
       given(eventMapper.toEventDto(entities.get(1))).willReturn(dto2);

       EventService spy = spy(eventService);

       given(eventsCache.get(eq(chatId), any())).willAnswer(invocation -> {
           java.util.function.Function<Long, Map<ChatEventType, Set<EventDto>>> loader = invocation.getArgument(1);
           return loader.apply(chatId);
       });

       var result = spy.getCachedChatEvents(chatId);
       assertThat(result).isNotNull();

       verify(eventsCache).get(eq(chatId), any());
       verify(eventRepository).findByChatId(chatId);
   }


   @Test
   void shouldReturnTrueIfMemberRoleIsLowerOrEqual() {
       assertThat(eventService.isEventRoleHighEnough(50, 30)).isTrue();
       assertThat(eventService.isEventRoleHighEnough(50, 50)).isTrue();
       assertThat(eventService.isEventRoleHighEnough(50, 60)).isFalse();
   }

   @Test
   void shouldDetectCommandEvent() {
       EventEntity event = new EventEntity();
       event.setRolePriority(CHAT_MANAGER_ROLE_PRIORITY);
       assertThat(eventService.isEventACommandEvent(event)).isTrue();
       event.setRolePriority(50);
       assertThat(eventService.isEventACommandEvent(event)).isFalse();
   }


    private void mockCommandValidation(boolean granted) {
        Command mockAnnotation = mock(Command.class);
        given(mockAnnotation.eventable()).willReturn(true);
        given(mockAnnotation.mainCommandName()).willReturn(userCommand);
        given(commandRegistry.getCommandAnnotation(userCommand)).willReturn(Optional.of(mockAnnotation));
        given(memberService.getMemberRolePriority(chatId, fromId)).willReturn(30);
        given(commandAccessService.checkCommandAuthorization(chatId, userCommand, 30, fromId)).willReturn(granted);

    }

    private EventEntity createBasicEvent() {
        EventEntity event = new EventEntity();
        event.setId(eventId);
        event.setChatId(chatId);
        event.setRolePriority(rolePriority);
        event.setType( WORD_FILTER);
        return event;
    }
    private EventDto createEventDto(long id, MyEventType type, int rolePriority, String argument, String fullCommand, long creatorId) {
            return new EventDto(
                    id, type, rolePriority, null, argument, creatorId, fullCommand,
                    null, null, null, null, null, null, null,
                    false, false, false
            );
    }
}}