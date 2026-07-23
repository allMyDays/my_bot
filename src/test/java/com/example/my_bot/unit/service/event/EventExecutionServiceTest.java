package com.example.my_bot.unit.service.event;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.client.VkCommunityClient;
import com.example.my_bot.command.CommandDispatcher;
import com.example.my_bot.dto.*;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.command.CommandRoutingData;
import com.example.my_bot.dto.event.DataForEventExecution;
import com.example.my_bot.dto.event.EventDto;
import com.example.my_bot.dto.event.ExecuteChatEventsResult;
import com.example.my_bot.enumeration.TimeZoneType;
import com.example.my_bot.enumeration.event.ChatEventType;
import com.example.my_bot.enumeration.event.MyEventType;
import com.example.my_bot.enumeration.event.ReactionType;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.service.BanService;
import com.example.my_bot.service.MemberService;
import com.example.my_bot.service.MessageLogService;
import com.example.my_bot.service.chat.AdminChatActionService;
import com.example.my_bot.service.chat.ChatService;
import com.example.my_bot.service.event.EventExecutionService;
import com.example.my_bot.service.event.EventService;
import com.example.my_bot.vk.enumeration.VideoType;
import com.example.my_bot.vk.enumeration.VkActionType;
import com.example.my_bot.vk.enumeration.VkMessageAttachmentType;
import com.example.my_bot.vk.mapping.action.VkAction;
import com.example.my_bot.vk.mapping.attachment.Video;
import com.example.my_bot.vk.mapping.attachment.VkMessageAttachment;
import com.github.benmanes.caffeine.cache.Cache;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.objects.messages.ForeignMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalTime;
import java.util.*;

import static com.example.my_bot.enumeration.event.ChatEventType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventExecutionServiceTest {

    @Mock
    private EventService eventService;

    @Mock
    private MemberService memberService;

    @Mock
    private BanService banService;

    @Mock
    private MessageMapper messageMapper;

    @Mock
    private VkChatClient vkChatClient;

    @Mock
    private VkCommunityClient vkCommunityClient;

    @Mock
    private ChatService chatService;

    @Mock
    private MessageLogService messageLogService;

    @Mock
    private AdminChatActionService adminChatActionService;

    @Mock
    private CommandDispatcher commandDispatcher;

    @InjectMocks
    private EventExecutionService eventExecutionService;

    private final long chatId = 1L;
    private final long fromId = 100L;
    private final int callerRole = 30;
    private final int conversationMessageId = 123;
    private final String userText = "test message";
    private final String fullCommand = "/test %from_id%";
    private final CommandRoutingData routingData = new CommandRoutingData();

    @BeforeEach
    void setUp() throws Exception {
        routingData.setDataBaseChatId(chatId);
        routingData.setVkApiChatId(chatId);
        routingData.setExecutorBot(new GroupActor(111L, "token"));
        eventExecutionService.setCommandDispatcher(commandDispatcher);

        clearCaches();
    }

    private void clearCaches() throws Exception {
        Field field = EventExecutionService.class.getDeclaredField("advancedEventCallCounters");
        field.setAccessible(true);
        Cache<?, ?> cache = (Cache<?, ?>) field.get(eventExecutionService);
        cache.asMap().clear();

        field = EventExecutionService.class.getDeclaredField("advancedEventCalls");
        field.setAccessible(true);
        cache = (Cache<?, ?>) field.get(eventExecutionService);
        cache.asMap().clear();

        field = EventExecutionService.class.getDeclaredField("eventCoolDownCalls");
        field.setAccessible(true);
        cache = (Cache<?, ?>) field.get(eventExecutionService);
        cache.asMap().clear();

        field = EventExecutionService.class.getDeclaredField("SAME_MESSAGES_CACHE");
        field.setAccessible(true);
        cache = (Cache<?, ?>) field.get(eventExecutionService);
        cache.asMap().clear();

        field = EventExecutionService.class.getDeclaredField("COMMUNITY_SUBSCRIPTIONS_CACHE");
        field.setAccessible(true);
        cache = (Cache<?, ?>) field.get(eventExecutionService);
        cache.asMap().clear();
    }


    private DataForEventExecution createBasicData() {
        return new DataForEventExecution(
                chatId, fromId, conversationMessageId,
                null, null, null, null, null, null,
                false, routingData, false
        );
    }

    private DataForEventExecution createDataWithText(String text) {
        return new DataForEventExecution(
                chatId, fromId, conversationMessageId,
                null, null, text, null, null, null,
                false, routingData, false
        );
    }

    private DataForEventExecution createDataWithAction(VkAction action) {
        return new DataForEventExecution(
                chatId, fromId, conversationMessageId,
                action, null, null, null, null, null,
                false, routingData, false
        );
    }

    private DataForEventExecution createDataWithAttachments(List<VkMessageAttachment> attachments) {
        return new DataForEventExecution(
                chatId, fromId, conversationMessageId,
                null, attachments, null, null, null, null,
                false, routingData, false
        );
    }

    private DataForEventExecution createDataWithReaction(ReactionType reactionType) {
        return new DataForEventExecution(
                chatId, fromId, conversationMessageId,
                null, null, null, null, null, reactionType,
                false, routingData, false
        );
    }

    private DataForEventExecution createDataWithFwMessages(List<ForeignMessage> fwMessages) {
        return new DataForEventExecution(
                chatId, fromId, conversationMessageId,
                null, null, null, fwMessages, null, null,
                false, routingData, false
        );
    }


    private EventDto createEventDto(long id, MyEventType type, int rolePriority, String argument,
                                    String fullCommand, boolean isAdvanced, boolean delete, boolean reply, boolean silent) {
        Integer aeMaxUsage = isAdvanced ? 5 : null;
        Integer aePeriodSec = isAdvanced ? 60 : null;
        return new EventDto(
                id, type, rolePriority, null, argument, fromId, fullCommand,
                aeMaxUsage, aePeriodSec, null, null, null, null, null,
                delete, reply, silent
        );
    }


    @Test
    void shouldNotExecuteEventsIfConditionsNotMet() throws ClientException, ApiException {
        DataForEventExecution data = createDataWithText("some text");

        EventDto eventDto = createEventDto(1L, MyEventType.WORD_FILTER, 50, "test", "/cmd", false, false, false, false);
        ImmutableMap<ChatEventType, ImmutableSet<EventDto>> eventsCache = ImmutableMap.of(
                TEXT, ImmutableSet.of(eventDto)
        );
        given(eventService.getCachedChatEvents(chatId)).willReturn(eventsCache);
        given(memberService.getMemberRolePriority(chatId, fromId)).willReturn(callerRole);
        given(chatService.getChatTimeZone(chatId)).willReturn(TimeZoneType.GMT_PLUS_3);

        ExecuteChatEventsResult result = eventExecutionService.executeRequiredChatEvents(data);
        assertThat(result.getExecutedEventsCounter()).isZero();
        verify(commandDispatcher, never()).dispatch(any());
        verify(adminChatActionService, never()).sendMessageAboutAnExecutedEvent(anyLong(), any(), anyString(), anyLong());
    }

    @Test
    void shouldExecuteTextEventWordFilter() throws ClientException, ApiException {
        DataForEventExecution data = createDataWithText("test test test");

        EventDto eventDto = createEventDto(1L, MyEventType.WORD_FILTER, 10, "test", "/cmd", false, false, false, false);
        ImmutableMap<ChatEventType, ImmutableSet<EventDto>> eventsCache = ImmutableMap.of(
                TEXT, ImmutableSet.of(eventDto)
        );
        given(eventService.getCachedChatEvents(chatId)).willReturn(eventsCache);
        given(memberService.getMemberRolePriority(chatId, fromId)).willReturn(5);
        given(chatService.getChatTimeZone(chatId)).willReturn(TimeZoneType.GMT_PLUS_3);

        given(eventService.isEventRoleHighEnough(10, 5)).willReturn(true);

        CommandMessageDto commandMessage = mock(CommandMessageDto.class);
        given(messageMapper.toCommandMessageDto(any(), anyLong(), anyString(), anyInt(), anyBoolean(), anyBoolean()))
                .willReturn(commandMessage);
        doNothing().when(commandDispatcher).dispatch(any());

        ExecuteChatEventsResult result = eventExecutionService.executeRequiredChatEvents(data);
        assertThat(result.getExecutedEventsCounter()).isEqualTo(1);
        verify(commandDispatcher).dispatch(any());
        verify(adminChatActionService).sendMessageAboutAnExecutedEvent(chatId, MyEventType.WORD_FILTER, "/cmd", fromId);
    }

    @Test
    void shouldExecuteAdvancedEventWithLimit() throws ClientException, ApiException {
        DataForEventExecution data = createDataWithText("test test test");

        EventDto eventDto = new EventDto(
                1L, MyEventType.WORD_FILTER, 10, null, "test", fromId, "/cmd",
                2, 60, null, null, null, null, null,
                false, false, false
        );

        ImmutableMap<ChatEventType, ImmutableSet<EventDto>> eventsCache = ImmutableMap.of(
                TEXT, ImmutableSet.of(eventDto)
        );
        given(eventService.getCachedChatEvents(chatId)).willReturn(eventsCache);
        given(memberService.getMemberRolePriority(chatId, fromId)).willReturn(5);
        given(chatService.getChatTimeZone(chatId)).willReturn(TimeZoneType.GMT_PLUS_3);

        given(eventService.isEventACommandEvent(eventDto)).willReturn(true);
        given(eventService.isEventRoleHighEnough(10, 5)).willReturn(true);

        CommandMessageDto commandMessage = new CommandMessageDto();
        given(messageMapper.toCommandMessageDto(any(), anyLong(), anyString(), anyInt(), anyBoolean(), anyBoolean()))
                .willReturn(commandMessage);
        doNothing().when(commandDispatcher).dispatch(any());

        ExecuteChatEventsResult result = eventExecutionService.executeRequiredChatEvents(data);
        assertThat(result.getExecutedEventsCounter()).isEqualTo(1);
        verify(commandDispatcher, times(1)).dispatch(any());

        data.setExecutedEventsCounter(0);
        result = eventExecutionService.executeRequiredChatEvents(data);
        assertThat(result.getExecutedEventsCounter()).isEqualTo(1);
        verify(commandDispatcher, times(2)).dispatch(any());
    }

    @Test
    void shouldNotExecuteIfCooldownActive() throws ClientException, ApiException {
        DataForEventExecution data1 = createDataWithText("test");
        DataForEventExecution data2 = createDataWithText("test");

        EventDto eventDto = new EventDto(
                1L, MyEventType.WORD_FILTER, 10, null, "test", fromId, "/cmd",
                null, null, null, null, 60, null, null,
                false, false, false
        );

        ImmutableMap<ChatEventType, ImmutableSet<EventDto>> eventsCache = ImmutableMap.of(
                TEXT, ImmutableSet.of(eventDto)
        );
        given(eventService.getCachedChatEvents(chatId)).willReturn(eventsCache);
        given(memberService.getMemberRolePriority(chatId, fromId)).willReturn(5);
        given(chatService.getChatTimeZone(chatId)).willReturn(TimeZoneType.GMT_PLUS_3);

        given(eventService.isEventACommandEvent(eventDto)).willReturn(true);
        given(eventService.isEventRoleHighEnough(10, 5)).willReturn(true);

        CommandMessageDto commandMessage = new CommandMessageDto();
        given(messageMapper.toCommandMessageDto(any(), anyLong(), anyString(), anyInt(), anyBoolean(), anyBoolean()))
                .willReturn(commandMessage);
        doNothing().when(commandDispatcher).dispatch(any());

        ExecuteChatEventsResult result = eventExecutionService.executeRequiredChatEvents(data1);
        assertThat(result.getExecutedEventsCounter()).isEqualTo(1);

        result = eventExecutionService.executeRequiredChatEvents(data2);
        assertThat(result.getExecutedEventsCounter()).isZero();
        verify(commandDispatcher, times(1)).dispatch(any());
    }

    @Test
    void shouldHandleActionEventInviteAnother() throws ClientException, ApiException {
        VkAction action = new VkAction();
        action.setType(VkActionType.CHAT_INVITE_USER);
        action.setMemberId(200L);
        DataForEventExecution data = createDataWithAction(action);

        EventDto eventDto = createEventDto(1L, MyEventType.INVITE_ANOTHER, 10, null, "/cmd", false, false, false, false);
        ImmutableMap<ChatEventType, ImmutableSet<EventDto>> eventsCache = ImmutableMap.of(
                ACTION, ImmutableSet.of(eventDto)
        );
        given(eventService.getCachedChatEvents(chatId)).willReturn(eventsCache);
        given(memberService.getMemberRolePriority(chatId, fromId)).willReturn(5);
        given(chatService.getChatTimeZone(chatId)).willReturn(TimeZoneType.GMT_PLUS_3);

        given(eventService.isEventACommandEvent(eventDto)).willReturn(true);
        given(eventService.isEventRoleHighEnough(10, 5)).willReturn(true);

        CommandMessageDto commandMessage = new CommandMessageDto();
        given(messageMapper.toCommandMessageDto(any(), anyLong(), anyString(), anyInt(), anyBoolean(), anyBoolean()))
                .willReturn(commandMessage);
        doNothing().when(commandDispatcher).dispatch(any());

        ExecuteChatEventsResult result = eventExecutionService.executeRequiredChatEvents(data);
        assertThat(result.getExecutedEventsCounter()).isEqualTo(1);
        verify(commandDispatcher).dispatch(any());
    }

    @Test
    void shouldHandleAttachmentEventVideoMessage() throws ClientException, ApiException {
        VkMessageAttachment attachment = new VkMessageAttachment();
        attachment.setType(VkMessageAttachmentType.VIDEO);
        Video video = new Video();
        video.setType(VideoType.VIDEO_MESSAGE);
        attachment.setVideo(video);
        DataForEventExecution data = createDataWithAttachments(List.of(attachment));

        EventDto eventDto = createEventDto(1L, MyEventType.VIDEO_MESSAGE, 10, null, "/cmd", false, false, false, false);
        ImmutableMap<ChatEventType, ImmutableSet<EventDto>> eventsCache = ImmutableMap.of(
                ATTACHMENTS, ImmutableSet.of(eventDto)
        );
        given(eventService.getCachedChatEvents(chatId)).willReturn(eventsCache);
        given(memberService.getMemberRolePriority(chatId, fromId)).willReturn(5);
        given(chatService.getChatTimeZone(chatId)).willReturn(TimeZoneType.GMT_PLUS_3);

        given(eventService.isEventACommandEvent(eventDto)).willReturn(true);
        given(eventService.isEventRoleHighEnough(10, 5)).willReturn(true);

        CommandMessageDto commandMessage = new CommandMessageDto();
        given(messageMapper.toCommandMessageDto(any(), anyLong(), anyString(), anyInt(), anyBoolean(), anyBoolean()))
                .willReturn(commandMessage);
        doNothing().when(commandDispatcher).dispatch(any());

        ExecuteChatEventsResult result = eventExecutionService.executeRequiredChatEvents(data);
        assertThat(result.getExecutedEventsCounter()).isEqualTo(1);
        verify(commandDispatcher).dispatch(any());
    }

    @Test
    void shouldHandleReactionEvent() throws ClientException, ApiException {
        ReactionType reactionType = ReactionType.HEART;
        DataForEventExecution data = createDataWithReaction(reactionType);

        EventDto eventDto = createEventDto(1L, MyEventType.REACTION_FILTER, 10, "1", "/cmd", false, false, false, false);
        ImmutableMap<ChatEventType, ImmutableSet<EventDto>> eventsCache = ImmutableMap.of(
                REACTION, ImmutableSet.of(eventDto)
        );
        given(eventService.getCachedChatEvents(chatId)).willReturn(eventsCache);
        given(memberService.getMemberRolePriority(chatId, fromId)).willReturn(5);
        given(chatService.getChatTimeZone(chatId)).willReturn(TimeZoneType.GMT_PLUS_3);

        given(eventService.isEventACommandEvent(eventDto)).willReturn(true);
        given(eventService.isEventRoleHighEnough(10, 5)).willReturn(true);

        CommandMessageDto commandMessage = new CommandMessageDto();
        given(messageMapper.toCommandMessageDto(any(), anyLong(), anyString(), anyInt(), anyBoolean(), anyBoolean()))
                .willReturn(commandMessage);
        doNothing().when(commandDispatcher).dispatch(any());

        ExecuteChatEventsResult result = eventExecutionService.executeRequiredChatEvents(data);
        assertThat(result.getExecutedEventsCounter()).isEqualTo(1);
        verify(commandDispatcher).dispatch(any());
    }

    @Test
    void shouldHandleFwdEvent() throws ClientException, ApiException {
        ForeignMessage fwd1 = new ForeignMessage();
        fwd1.setFwdMessages(List.of());
        ForeignMessage fwd2 = new ForeignMessage();
        fwd2.setFwdMessages(List.of());
        DataForEventExecution data = createDataWithFwMessages(List.of(fwd1, fwd2));

        EventDto eventDto = createEventDto(1L, MyEventType.FWD_QUANTITY, 10, "2", "/cmd", false, false, false, false);
        ImmutableMap<ChatEventType, ImmutableSet<EventDto>> eventsCache = ImmutableMap.of(
                FORWARDS, ImmutableSet.of(eventDto)
        );
        given(eventService.getCachedChatEvents(chatId)).willReturn(eventsCache);
        given(memberService.getMemberRolePriority(chatId, fromId)).willReturn(5);
        given(chatService.getChatTimeZone(chatId)).willReturn(TimeZoneType.GMT_PLUS_3);

        given(eventService.isEventACommandEvent(eventDto)).willReturn(true);
        given(eventService.isEventRoleHighEnough(10, 5)).willReturn(true);

        CommandMessageDto commandMessage = new CommandMessageDto();
        given(messageMapper.toCommandMessageDto(any(), anyLong(), anyString(), anyInt(), anyBoolean(), anyBoolean()))
                .willReturn(commandMessage);
        doNothing().when(commandDispatcher).dispatch(any());

        ExecuteChatEventsResult result = eventExecutionService.executeRequiredChatEvents(data);
        assertThat(result.getExecutedEventsCounter()).isEqualTo(1);
        verify(commandDispatcher).dispatch(any());
    }

    @Test
    void testInsertRequiredMemberIntoTheCommand() throws Exception {
        EventDto eventDto = createEventDto(1L, MyEventType.WORD_FILTER, 10, "test", "/cmd %from_id% %member_id%", false, false, false, false);
        VkAction action = new VkAction();
        action.setMemberId(200L);
        DataForEventExecution data = createDataWithAction(action);

        given(eventService.isEventACommandEvent(eventDto)).willReturn(true);

        java.lang.reflect.Method method = EventExecutionService.class.getDeclaredMethod(
                "insertRequiredMemberIntoTheCommand", EventDto.class, DataForEventExecution.class);
        method.setAccessible(true);
        String result = (String) method.invoke(eventExecutionService, eventDto, data);

        assertThat(result).contains("vk.com/id100").contains("vk.com/id200");
    }

    @Test
    void testIsCurrentTimeInRequiredDailyRange() throws Exception {
        java.lang.reflect.Method method = EventExecutionService.class.getDeclaredMethod(
                "isCurrentTimeInRequiredDailyRange", LocalTime.class, LocalTime.class, LocalTime.class);
        method.setAccessible(true);

        LocalTime start = LocalTime.of(8, 0);
        LocalTime end = LocalTime.of(18, 0);
        LocalTime now = LocalTime.of(12, 0);
        boolean result = (boolean) method.invoke(eventExecutionService, start, end, now);
        assertThat(result).isTrue();

        now = LocalTime.of(20, 0);
        result = (boolean) method.invoke(eventExecutionService, start, end, now);
        assertThat(result).isFalse();

        // Переход через полночь
        start = LocalTime.of(22, 0);
        end = LocalTime.of(6, 0);
        now = LocalTime.of(23, 0);
        result = (boolean) method.invoke(eventExecutionService, start, end, now);
        assertThat(result).isTrue();

        now = LocalTime.of(5, 0);
        result = (boolean) method.invoke(eventExecutionService, start, end, now);
        assertThat(result).isTrue();

        now = LocalTime.of(12, 0);
        result = (boolean) method.invoke(eventExecutionService, start, end, now);
        assertThat(result).isFalse();
    }

    @Test
    void testIsNewMember() throws Exception {
        java.lang.reflect.Method method = EventExecutionService.class.getDeclaredMethod(
                "isNewMember", Instant.class, Instant.class, int.class);
        method.setAccessible(true);

        Instant now = Instant.now();
        Instant memberJoin = now.minusSeconds(100);
        int period = 60;
        boolean result = (boolean) method.invoke(eventExecutionService, now, memberJoin, period);
        assertThat(result).isFalse();

        memberJoin = now.minusSeconds(30);
        result = (boolean) method.invoke(eventExecutionService, now, memberJoin, period);
        assertThat(result).isTrue();
    }
}