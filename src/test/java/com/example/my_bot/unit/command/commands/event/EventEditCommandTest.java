package com.example.my_bot.unit.command.commands.event;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.commands.event.EventEditCommand;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.command.CommandRoutingData;
import com.example.my_bot.dto.event.EventDto;
import com.example.my_bot.entity.EventEntity;
import com.example.my_bot.enumeration.TimeZoneType;
import com.example.my_bot.enumeration.command.CommandExecutionStatus;
import com.example.my_bot.enumeration.event.MyEventType;
import com.example.my_bot.enumeration.user.NameCase;
import com.example.my_bot.exception.event.EventException;
import com.example.my_bot.exception.role.RoleException;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.resolver.UserInputResolver;
import com.example.my_bot.service.GlobalUserService;
import com.example.my_bot.service.RoleService;
import com.example.my_bot.service.chat.ChatService;
import com.example.my_bot.service.event.EventService;
import com.example.my_bot.utils.TimeUtils;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static com.example.my_bot.utils.TextUtils.createMention;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.vk.api.sdk.objects.base.Error;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EventEditCommandTest {

    private static final long CHAT_ID = 100L;
    private static final long FROM_ID = 200L;
    private static final long EVENT_ID = 1L;
    private static final int OUTER_ID = 1;
    private static final String EVENT_DESCRIPTION = "приглашение новых пользователей";
    private static final String ROLE_NAME = "Модератор";
    private static final int ROLE_PRIORITY = 5;
    private static final long MEMBER_ID = 300L;
    private static final String MEMBER_NAME_GENITIVE = "Ивана Иванова";
    private static final TimeZoneType TIME_ZONE = TimeZoneType.GMT_PLUS_3;

    @Mock
    private VkChatClient vkChatClient;

    @Mock
    private EventService eventService;

    @Mock
    private RoleService roleService;

    @Mock
    private ChatService chatService;

    @Mock
    private UserInputResolver userInputResolver;

    @Mock
    private GlobalUserService globalUserService;

    @Mock
    private MessageMapper messageMapper;

    @Mock
    private CommandMessageDto commandMessage;

    @Mock
    private CommandRoutingData commandRoutingData;

    @Mock
    private GroupActor groupActor;

    private EventEditCommand eventEditCommand;

    @BeforeEach
    void setUp() {
        eventEditCommand = new EventEditCommand(
                vkChatClient,
                eventService,
                roleService,
                chatService,
                userInputResolver,
                globalUserService,
                messageMapper
        );

        when(commandMessage.getCommandRoutingData()).thenReturn(commandRoutingData);
        when(commandRoutingData.getDataBaseChatId()).thenReturn(CHAT_ID);
        when(commandMessage.getFromId()).thenReturn(FROM_ID);
    }

    @Test
    void shouldSetDailyWorkTimeSuccess() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{"1", "времяработы", "23:00-08:00"});

        EventDto eventDto = createEventDto(MyEventType.INVITE_ANOTHER);
        when(eventService.getEventsSortedByIdInIncreasingOrder(CHAT_ID)).thenReturn(List.of(eventDto));

        EventEntity editedEvent = createEventEntity(MyEventType.INVITE_ANOTHER);
        editedEvent.setStartDayTime(LocalTime.of(23, 0));
        editedEvent.setEndDayTime(LocalTime.of(8, 0));
        when(eventService.setDailyWorkTime(eq(EVENT_ID), any(LocalTime.class), any(LocalTime.class), eq(FROM_ID)))
                .thenReturn(editedEvent);

        when(chatService.getChatTimeZone(CHAT_ID)).thenReturn(TIME_ZONE);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(new SendMessageDto("", 1L, groupActor, null, false, false, null));

        CommandExecutionStatus status = eventEditCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);
        verify(vkChatClient).sendText(any(SendMessageDto.class));
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        String actual = captor.getValue().getText();
        assertTrue(actual.contains("✅Теперь событие №1 («Приглашение в чат») будет работать каждый день с 23:00 до 08:00 GMT+3."));
    }

    @Test
    void shouldRemoveDailyWorkTimeSuccess() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{"1", "времяработы", "удалить"});

        EventDto eventDto = createEventDto(MyEventType.INVITE_ANOTHER);
        when(eventService.getEventsSortedByIdInIncreasingOrder(CHAT_ID)).thenReturn(List.of(eventDto));

        EventEntity editedEvent = createEventEntity(MyEventType.INVITE_ANOTHER);
        when(eventService.removeDailyWorkTime(EVENT_ID, FROM_ID)).thenReturn(editedEvent);

        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(new SendMessageDto("", 1L, groupActor, null, false, false, null));

        CommandExecutionStatus status = eventEditCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        String actual = captor.getValue().getText();
        assertTrue(actual.contains("✅Теперь событие №1 («Приглашение в чат») будет работать 24/7 независимо от времени дня."));
    }

    @Test
    void shouldAddExceptionalMemberSuccess() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{"1", "исключение", "@durov"});

        EventDto eventDto = createEventDto(MyEventType.INVITE_ANOTHER);
        when(eventService.getEventsSortedByIdInIncreasingOrder(CHAT_ID)).thenReturn(List.of(eventDto));

        when(userInputResolver.getMemberIdByStringInput(CHAT_ID, "@durov")).thenReturn(Optional.of(MEMBER_ID));

        EventEntity editedEvent = createEventEntity(MyEventType.INVITE_ANOTHER);
        when(eventService.addMemberToExceptional(EVENT_ID, MEMBER_ID, FROM_ID)).thenReturn(editedEvent);

        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(new SendMessageDto("", 1L, groupActor, null, false, false, null));

        CommandExecutionStatus status = eventEditCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        String actual = captor.getValue().getText();
        assertTrue(actual.contains("✅Вы успешно добавили данного участника в исключения для события  №1 («Приглашение в чат»)."));
    }

    @Test
    void shouldRemoveExceptionalMemberSuccess() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{"1", "исключение", "удалить", "@durov"});

        EventDto eventDto = createEventDto(MyEventType.INVITE_ANOTHER);
        when(eventService.getEventsSortedByIdInIncreasingOrder(CHAT_ID)).thenReturn(List.of(eventDto));

        when(userInputResolver.getMemberIdByStringInput(CHAT_ID, "@durov")).thenReturn(Optional.of(MEMBER_ID));

        EventEntity editedEvent = createEventEntity(MyEventType.INVITE_ANOTHER);
        when(eventService.removeMemberFromExceptional(EVENT_ID, MEMBER_ID, FROM_ID)).thenReturn(editedEvent);

        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(new SendMessageDto("", 1L, groupActor, null, false, false, null));

        CommandExecutionStatus status = eventEditCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        String actual = captor.getValue().getText();
        assertTrue(actual.contains("✅Вы успешно удалили данного участника из исключения события  №1 («Приглашение в чат»)"));
    }

    @Test
    void shouldSetActionLimitSuccess() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{"1", "лимитдействия", "100", "2", "часа"});

        EventDto eventDto = createEventDto(MyEventType.INVITE_ANOTHER);
        when(eventService.getEventsSortedByIdInIncreasingOrder(CHAT_ID)).thenReturn(List.of(eventDto));

        long periodSec = 7200L;
        try (MockedStatic<TimeUtils> timeUtils = mockStatic(TimeUtils.class)) {
            timeUtils.when(() -> TimeUtils.toSecondsFromString("2", "часа")).thenReturn(Optional.of(periodSec));

            EventEntity editedEvent = createEventEntity(MyEventType.INVITE_ANOTHER);
            editedEvent.setAEMaxUsage(100);
            editedEvent.setAEPeriodSec((int) periodSec);
            when(eventService.setAETimePeriodAndMaxUsage(EVENT_ID, periodSec, 100, FROM_ID)).thenReturn(editedEvent);

            when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(new SendMessageDto("", 1L, groupActor, null, false, false, null));

            CommandExecutionStatus status = eventEditCommand.execute(commandMessage);

            assertEquals(CommandExecutionStatus.SUCCESS, status);
            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            String actual = captor.getValue().getText();
            System.out.println(actual);
            assertTrue(actual.contains("✅Вы успешно добавили событию №1 («Приглашение в чат») лимит действия"));
        }
    }

    @Test
    void shouldSetCooldownSuccess() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{"1", "кулдаун", "5", "минут"});

        EventDto eventDto = createEventDto(MyEventType.INVITE_ANOTHER);
        when(eventService.getEventsSortedByIdInIncreasingOrder(CHAT_ID)).thenReturn(List.of(eventDto));

        long periodSec = 300L;
        try (MockedStatic<TimeUtils> timeUtils = mockStatic(TimeUtils.class)) {
            timeUtils.when(() -> TimeUtils.toSecondsFromString("5", "минут")).thenReturn(Optional.of(periodSec));

            EventEntity editedEvent = createEventEntity(MyEventType.INVITE_ANOTHER);
            editedEvent.setCDPeriodSec((int) periodSec);
            when(eventService.setCDTimePeriod(EVENT_ID, periodSec, FROM_ID)).thenReturn(editedEvent);

            when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(new SendMessageDto("", 1L, groupActor, null, false, false, null));

            CommandExecutionStatus status = eventEditCommand.execute(commandMessage);

            assertEquals(CommandExecutionStatus.SUCCESS, status);
            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            String actual = captor.getValue().getText();
            assertTrue(actual.contains("✅Вы успешно добавили событию №1 («Приглашение в чат») кулдаун срабатывания."));
        }
    }

    @Test
    void shouldRemoveCooldownWhenPeriodIsZero() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{"1", "кулдаун", "0", "сек"});

        EventDto eventDto = createEventDto(MyEventType.INVITE_ANOTHER);
        when(eventService.getEventsSortedByIdInIncreasingOrder(CHAT_ID)).thenReturn(List.of(eventDto));

        long periodSec = 0L;
        try (MockedStatic<TimeUtils> timeUtils = mockStatic(TimeUtils.class)) {
            timeUtils.when(() -> TimeUtils.toSecondsFromString("0", "сек")).thenReturn(Optional.of(periodSec));

            EventEntity editedEvent = createEventEntity(MyEventType.INVITE_ANOTHER);
            editedEvent.setCDPeriodSec(0);
            when(eventService.setCDTimePeriod(EVENT_ID, periodSec, FROM_ID)).thenReturn(editedEvent);

            when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(new SendMessageDto("", 1L, groupActor, null, false, false, null));

            CommandExecutionStatus status = eventEditCommand.execute(commandMessage);

            assertEquals(CommandExecutionStatus.SUCCESS, status);
            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            String actual = captor.getValue().getText();
            assertTrue(actual.contains("✅Вы успешно отключили кулдаун срабатывания для события №1 («Приглашение в чат»)."));
        }
    }

    @Test
    void shouldRemoveNewMembersSuccess() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{"1", "новички", "удалить"});

        EventDto eventDto = createEventDto(MyEventType.INVITE_ANOTHER);
        when(eventService.getEventsSortedByIdInIncreasingOrder(CHAT_ID)).thenReturn(List.of(eventDto));

        EventEntity editedEvent = createEventEntity(MyEventType.INVITE_ANOTHER);
        when(eventService.removeNewMembersTimePeriod(EVENT_ID, FROM_ID)).thenReturn(editedEvent);

        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(new SendMessageDto("", 1L, groupActor, null, false, false, null));

        CommandExecutionStatus status = eventEditCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        String actual = captor.getValue().getText();
        assertTrue(actual.contains("✅Теперь событие №1 («Приглашение в чат») будет срабатывать на участников независимо от того, являются они новичками или нет."));
    }

    @Test
    void shouldSetRoleByPrioritySuccess() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{"1", "роль", "5"});

        EventDto eventDto = createEventDto(MyEventType.INVITE_ANOTHER);
        when(eventService.getEventsSortedByIdInIncreasingOrder(CHAT_ID)).thenReturn(List.of(eventDto));

        EventEntity editedEvent = createEventEntity(MyEventType.INVITE_ANOTHER);
        editedEvent.setRolePriority(5);
        when(eventService.setNewRole(eq(EVENT_ID), eq(5), eq(FROM_ID))).thenReturn(editedEvent);

        when(roleService.getRoleName(CHAT_ID, 5)).thenReturn(Optional.of(ROLE_NAME));

        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(new SendMessageDto("", 1L, groupActor, null, false, false, null));

        CommandExecutionStatus status = eventEditCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        String actual = captor.getValue().getText();
        assertTrue(actual.contains("✅Теперь событие №1 («Приглашение в чат») будет срабатывать на роль «Модератор» и ниже."));
    }

    @Test
    void shouldSetRoleByNameSuccess() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{"1", "роль", "Модератор"});

        EventDto eventDto = createEventDto(MyEventType.INVITE_ANOTHER);
        when(eventService.getEventsSortedByIdInIncreasingOrder(CHAT_ID)).thenReturn(List.of(eventDto));

        EventEntity editedEvent = createEventEntity(MyEventType.INVITE_ANOTHER);
        editedEvent.setRolePriority(5);
        when(eventService.setNewRole(eq(EVENT_ID), eq("Модератор"), eq(FROM_ID))).thenReturn(editedEvent);

        when(roleService.getRoleName(CHAT_ID, 5)).thenReturn(Optional.of(ROLE_NAME));

        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(new SendMessageDto("", 1L, groupActor, null, false, false, null));

        CommandExecutionStatus status = eventEditCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        String actual = captor.getValue().getText();
        assertTrue(actual.contains("✅Теперь событие №1 («Приглашение в чат») будет срабатывать на роль «Модератор» и ниже."));
    }

    @Test
    void shouldSetCommandSuccess() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{"1", "команда", "!kick", "%user%"});

        EventDto eventDto = createEventDto(MyEventType.INVITE_ANOTHER);
        when(eventService.getEventsSortedByIdInIncreasingOrder(CHAT_ID)).thenReturn(List.of(eventDto));

        EventEntity editedEvent = createEventEntity(MyEventType.INVITE_ANOTHER);
        editedEvent.setFullCommand("!kick %user%");
        when(eventService.setNewCommand(EVENT_ID, "!kick %user%", FROM_ID)).thenReturn(editedEvent);

        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(new SendMessageDto("", 1L, groupActor, null, false, false, null));

        CommandExecutionStatus status = eventEditCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        String actual = captor.getValue().getText();
        assertTrue(actual.contains("✅Новая команда для события №1 («Приглашение в чат») была успешно установлена."));
    }

    @Test
    void shouldReturnArgumentValidationErrorWhenNotEnoughArguments() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"1", "роль"});
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(new SendMessageDto("", 1L, groupActor, null, false, false, null));

        CommandExecutionStatus status = eventEditCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.ARGUMENT_VALIDATION_ERROR, status);
        verify(vkChatClient).sendText(any(SendMessageDto.class));
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        verify(eventService, never()).getEventsSortedByIdInIncreasingOrder(anyLong());
    }

    @Test
    void shouldReturnArgumentValidationErrorWhenInvalidEventId() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"abc", "роль", "Модератор"});
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(new SendMessageDto("", 1L, groupActor, null, false, false, null));

        CommandExecutionStatus status = eventEditCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.ARGUMENT_VALIDATION_ERROR, status);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        verify(eventService, never()).getEventsSortedByIdInIncreasingOrder(anyLong());
    }

    @Test
    void shouldReturnBusinessLogicErrorWhenEventNotFound() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"10", "роль", "Модератор"});
        EventDto eventDto = createEventDto(MyEventType.INVITE_ANOTHER);
        when(eventService.getEventsSortedByIdInIncreasingOrder(CHAT_ID)).thenReturn(List.of(eventDto)); // только 1 событие
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(new SendMessageDto("", 1L, groupActor, null, false, false, null));

        CommandExecutionStatus status = eventEditCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertEquals("Не найдено события с таким ID.", captor.getValue().getText());
        verify(eventService, never()).setNewRole(anyLong(), anyInt(), anyLong());
    }

    @Test
    void shouldReturnArgumentValidationErrorWhenInvalidEditType() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"1", "неверныйтип", "аргумент"});
        EventDto eventDto = createEventDto(MyEventType.INVITE_ANOTHER);
        when(eventService.getEventsSortedByIdInIncreasingOrder(CHAT_ID)).thenReturn(List.of(eventDto));
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(new SendMessageDto("", 1L, groupActor, null, false, false, null));

        CommandExecutionStatus status = eventEditCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.ARGUMENT_VALIDATION_ERROR, status);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertTrue(captor.getValue().getText().contains("несуществующий тип для редактирования"));
        verify(eventService, never()).setDailyWorkTime(anyLong(), any(), any(), anyLong());
    }

    @Test
    void shouldReturnArgumentValidationErrorWhenInvalidTimeRange() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{"1", "времяработы", "25:00-26:00"});
        EventDto eventDto = createEventDto(MyEventType.INVITE_ANOTHER);
        when(eventService.getEventsSortedByIdInIncreasingOrder(CHAT_ID)).thenReturn(List.of(eventDto));
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(new SendMessageDto("", 1L, groupActor, null, false, false, null));

        CommandExecutionStatus status = eventEditCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertTrue(captor.getValue().getText().contains("некорректный аргумент диапазона"));
        verify(eventService, never()).setDailyWorkTime(anyLong(), any(), any(), anyLong());
    }

    @Test
    void shouldReturnArgumentValidationErrorWhenInvalidMember() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{"1", "исключение", "неверный"});
        EventDto eventDto = createEventDto(MyEventType.INVITE_ANOTHER);
        when(eventService.getEventsSortedByIdInIncreasingOrder(CHAT_ID)).thenReturn(List.of(eventDto));
        when(userInputResolver.getMemberIdByStringInput(CHAT_ID, "неверный")).thenReturn(Optional.empty());
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(new SendMessageDto("", 1L, groupActor, null, false, false, null));

        CommandExecutionStatus status = eventEditCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.ARGUMENT_VALIDATION_ERROR, status);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertTrue(captor.getValue().getText().contains("Не удалось получить участника по указанному вами строчному аргументу."));
        verify(eventService, never()).addMemberToExceptional(anyLong(), anyLong(), anyLong());
    }

    @Test
    void shouldReturnArgumentValidationErrorWhenInvalidPeriod() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{"1", "кулдаун", "abc", "минут"});
        EventDto eventDto = createEventDto(MyEventType.INVITE_ANOTHER);
        when(eventService.getEventsSortedByIdInIncreasingOrder(CHAT_ID)).thenReturn(List.of(eventDto));
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(new SendMessageDto("", 1L, groupActor, null, false, false, null));

        CommandExecutionStatus status = eventEditCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.ARGUMENT_VALIDATION_ERROR, status);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        verify(eventService, never()).setCDTimePeriod(anyLong(), anyLong(), anyLong());
    }

    @Test
    void shouldReturnBusinessLogicErrorOnEventExceptionFromSetDailyWorkTime() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{"1", "времяработы", "23:00-08:00"});
        EventDto eventDto = createEventDto(MyEventType.INVITE_ANOTHER);
        when(eventService.getEventsSortedByIdInIncreasingOrder(CHAT_ID)).thenReturn(List.of(eventDto));
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(new SendMessageDto("", 1L, groupActor, null, false, false, null));

        EventException exception = new EventException("Ошибка установки времени") {};
        when(eventService.setDailyWorkTime(anyLong(), any(), any(), anyLong())).thenThrow(exception);

        CommandExecutionStatus status = eventEditCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertEquals(exception.getMessage(), captor.getValue().getText());
    }

    @Test
    void shouldReturnBusinessLogicErrorOnRoleExceptionFromSetRole() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{"1", "роль", "Модератор"});
        EventDto eventDto = createEventDto(MyEventType.INVITE_ANOTHER);
        when(eventService.getEventsSortedByIdInIncreasingOrder(CHAT_ID)).thenReturn(List.of(eventDto));
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(new SendMessageDto("", 1L, groupActor, null, false, false, null));

        RoleException exception = new RoleException("Роль не найдена") {};
        when(eventService.setNewRole(eq(EVENT_ID), eq("Модератор"), eq(FROM_ID))).thenThrow(exception);

        CommandExecutionStatus status = eventEditCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertEquals(exception.getMessage(), captor.getValue().getText());
    }

    @Test
    void shouldPropagateClientExceptionFromVkChatClientSendText() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{"1", "роль", "Модератор"});
        EventDto eventDto = createEventDto(MyEventType.INVITE_ANOTHER);
        when(eventService.getEventsSortedByIdInIncreasingOrder(CHAT_ID)).thenReturn(List.of(eventDto));
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(new SendMessageDto("", 1L, groupActor, null, false, false, null));

        EventEntity editedEvent = createEventEntity(MyEventType.INVITE_ANOTHER);
        editedEvent.setRolePriority(5);
        when(eventService.setNewRole(eq(EVENT_ID), eq("Модератор"), eq(FROM_ID))).thenReturn(editedEvent);
        when(roleService.getRoleName(CHAT_ID, 5)).thenReturn(Optional.of(ROLE_NAME));

        ClientException clientException = new ClientException("VK error");
        doThrow(clientException).when(vkChatClient).sendText(any(SendMessageDto.class));

        assertThrows(ClientException.class, () -> eventEditCommand.execute(commandMessage));
        verify(vkChatClient).sendText(any(SendMessageDto.class));
    }

    @Test
    void shouldPropagateApiExceptionFromVkChatClientSendText() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{"1", "роль", "Модератор"});
        EventDto eventDto = createEventDto(MyEventType.INVITE_ANOTHER);
        when(eventService.getEventsSortedByIdInIncreasingOrder(CHAT_ID)).thenReturn(List.of(eventDto));
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(new SendMessageDto("", 1L, groupActor, null, false, false, null));

        EventEntity editedEvent = createEventEntity(MyEventType.INVITE_ANOTHER);
        editedEvent.setRolePriority(5);
        when(eventService.setNewRole(eq(EVENT_ID), eq("Модератор"), eq(FROM_ID))).thenReturn(editedEvent);
        when(roleService.getRoleName(CHAT_ID, 5)).thenReturn(Optional.of(ROLE_NAME));

        ApiException apiException = new ApiException(new Error().setErrorCode(1).setErrorMsg("API error"));
        doThrow(apiException).when(vkChatClient).sendText(any(SendMessageDto.class));

        assertThrows(ApiException.class, () -> eventEditCommand.execute(commandMessage));
        verify(vkChatClient).sendText(any(SendMessageDto.class));
    }

    private EventDto createEventDto(MyEventType type) {
        return new EventDto(
                EVENT_ID,
                type,
                null,
                null,
                null,
                1L,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                false,
                false
        );
    }

    private EventEntity createEventEntity(MyEventType type) {
        EventEntity entity = new EventEntity();
        entity.setId(EVENT_ID);
        entity.setType(type);
        entity.setRolePriority(ROLE_PRIORITY);
        entity.setFullCommand("!ban");
        return entity;
    }
}