package com.example.my_bot.unit.command.commands.event;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.commands.event.AllEventsShowCommand;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.command.CommandRoutingData;
import com.example.my_bot.dto.event.EventDto;
import com.example.my_bot.enumeration.TimeZoneType;
import com.example.my_bot.enumeration.command.CommandExecutionStatus;
import com.example.my_bot.enumeration.event.MyEventType;
import com.example.my_bot.enumeration.user.NameCase;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.service.GlobalUserService;
import com.example.my_bot.service.RoleService;
import com.example.my_bot.service.chat.ChatService;
import com.example.my_bot.service.event.EventService;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.vk.api.sdk.objects.base.Error;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AllEventsShowCommandTest {

    private static final long CHAT_ID = 100L;
    private static final long FROM_ID = 200L;
    private static final long CREATOR_ID = 300L;
    private static final long MEMBER_ID = 400L;
    private static final String CHAT_TIME_ZONE = "GMT+3";
    private static final String USER_NAME_GENITIVE = "Ивана Иванова";

    @Mock
    private EventService eventService;

    @Mock
    private ChatService chatService;

    @Mock
    private GlobalUserService globalUserService;

    @Mock
    private MessageMapper messageMapper;

    @Mock
    private VkChatClient vkChatClient;

    @Mock
    private RoleService roleService;

    @Mock
    private CommandMessageDto commandMessage;

    @Mock
    private CommandRoutingData commandRoutingData;

    @Mock
    private GroupActor groupActor;

    private AllEventsShowCommand allEventsShowCommand;

    @BeforeEach
    void setUp() {
        allEventsShowCommand = new AllEventsShowCommand(
                eventService,
                chatService,
                globalUserService,
                messageMapper
        );
        allEventsShowCommand.setVkChatClient(vkChatClient);
        allEventsShowCommand.setRoleService(roleService);

        when(commandMessage.getCommandRoutingData()).thenReturn(commandRoutingData);
        when(commandRoutingData.getDataBaseChatId()).thenReturn(CHAT_ID);
        when(commandMessage.getFromId()).thenReturn(FROM_ID);
    }

    @Test
    void shouldShowAvailableEvents() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"доступные"});

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = allEventsShowCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);
        verify(vkChatClient).sendText(sendMessageDto);

        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        String actual = captor.getValue().getText();

        assertTrue(actual.contains("Вам доступно " + MyEventType.values().length + " событий для создания:"));
        for (MyEventType type : MyEventType.values()) {
            assertTrue(actual.contains(type.getCyrillicType()));
            assertTrue(actual.contains(type.getDescription()));
        }
        verify(eventService, never()).getEventsSortedByIdInIncreasingOrder(anyLong());
        verify(chatService, never()).getChatTimeZone(anyLong());
    }

    @Test
    void shouldShowEmptyEventsList() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});

        when(chatService.getChatTimeZone(CHAT_ID)).thenReturn(TimeZoneType.GMT_PLUS_3);
        when(eventService.getEventsSortedByIdInIncreasingOrder(CHAT_ID)).thenReturn(List.of());
        when(eventService.getMaxEvents()).thenReturn(10);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = allEventsShowCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);
        verify(vkChatClient).sendText(sendMessageDto);

        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        String actual = captor.getValue().getText();

        assertTrue(actual.contains("В чате установлено (0/10) событий:"));
        assertTrue(actual.contains("событий") || actual.contains("событий"));
        verify(globalUserService, never()).getUserFullNameInRequiredCase(anyLong(), any());
        verify(roleService, never()).getRoleName(anyLong(), anyInt());
    }

    @Test
    void shouldShowEventsListWithRegularEvents() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});

        when(chatService.getChatTimeZone(CHAT_ID)).thenReturn(TimeZoneType.GMT_PLUS_3);
        when(eventService.getMaxEvents()).thenReturn(10);

        EventDto event = new EventDto(
                1L,                              // id
                MyEventType.WITH_SUBSCRIPTION,   // type
                null,                            // rolePriority
                MEMBER_ID,                       // memberToTrigger
                "123",                           // argument
                CREATOR_ID,                      // creatorId
                "!ban",                          // fullCommand
                null,                            // AEMaxUsage
                null,                            // AEPeriodSec
                LocalTime.of(10, 0),      // startDayTime
                LocalTime.of(18, 0),      // endDayTime
                null,                              // CDPeriodSec
                Set.of(777L),                    // exceptionalMembers
                null,                            // newMembersPeriodSec
                false,                           // delete
                false,                           // reply
                false                            // silent
        );

        when(eventService.getEventsSortedByIdInIncreasingOrder(CHAT_ID)).thenReturn(List.of(event));

        when(globalUserService.getUserFullNameInRequiredCase(MEMBER_ID, NameCase.GENITIVE))
                .thenReturn(USER_NAME_GENITIVE);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = allEventsShowCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);
        verify(vkChatClient).sendText(sendMessageDto);
        verify(globalUserService).getUserFullNameInRequiredCase(MEMBER_ID, NameCase.GENITIVE);

        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        String actual = captor.getValue().getText();

        assertTrue(actual.contains("В чате установлено (1/10) событий:"));
        assertTrue(actual.contains("Выполнение команды «!ban» при событии «" + MyEventType.WITH_SUBSCRIPTION.getDescription() + "» для"));
        assertTrue(actual.contains("@id" + MEMBER_ID + "(" + USER_NAME_GENITIVE + ")"));
        assertTrue(actual.contains("Аргумент: @id123"));
        assertTrue(actual.contains("Работает ежедневно с 10:00 до 18:00 " + TimeZoneType.GMT_PLUS_3.getStringType()));
        assertTrue(actual.contains("❌Не реагирует на: @id777(1)"));
        assertFalse(actual.contains("специальных команд-событий"));
    }

    @Test
    void shouldShowCommandEventsSeparately() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});

        when(chatService.getChatTimeZone(CHAT_ID)).thenReturn(TimeZoneType.GMT_PLUS_3);
        when(eventService.getMaxEvents()).thenReturn(10);

        EventDto regularEvent = new EventDto(
                1L,
                MyEventType.SHORT_MESSAGE,
                null,
                MEMBER_ID,
                "10",
                CREATOR_ID,
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

        EventDto commandEvent = new EventDto(
                2L,
                MyEventType.REACTION_FILTER,
                5,
                null,
                "123",
                CREATOR_ID,
                "!kick",
                3,
                60,
                null,
                null,
                null,
                null,
                null,
                true,
                true,
                true
        );

        when(eventService.getEventsSortedByIdInIncreasingOrder(CHAT_ID)).thenReturn(List.of(regularEvent, commandEvent));

        when(eventService.isEventACommandEvent(regularEvent)).thenReturn(false);
        when(eventService.isEventACommandEvent(commandEvent)).thenReturn(true);

        when(globalUserService.getUserFullNameInRequiredCase(MEMBER_ID, NameCase.GENITIVE))
                .thenReturn(USER_NAME_GENITIVE);
        when(roleService.getRoleName(CHAT_ID, 5)).thenReturn(Optional.of("Модератор"));

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = allEventsShowCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);
        verify(vkChatClient).sendText(sendMessageDto);

        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        String actual = captor.getValue().getText();

        assertTrue(actual.contains("В чате установлено (2/10) событий:"));
        assertTrue(actual.contains("Выполнение команды «none» при событии «" + MyEventType.SHORT_MESSAGE.getDescription() + "» для"));
        assertTrue(actual.contains("Аргумент: 10 (символов)"));

        assertTrue(actual.contains("специальных команд-событий"));
        assertTrue(actual.contains("Выполнение системной команды «!kick» при аргументе «123» для события «" + MyEventType.REACTION_FILTER.getDescription() + "»."));
        assertTrue(actual.contains("\uD83D\uDDD1")); // delete
        assertTrue(actual.contains("↪")); // reply
        assertTrue(actual.contains("\uD83D\uDD15")); // silent

        assertFalse(actual.contains("достижении лимита действия"));
    }

    @Test
    void shouldShowRolePriorityWhenRoleNameNotFound() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});

        when(chatService.getChatTimeZone(CHAT_ID)).thenReturn(TimeZoneType.GMT_PLUS_3);
        when(eventService.getMaxEvents()).thenReturn(10);

        EventDto event = new EventDto(
                1L,
                MyEventType.WITHOUT_SUBSCRIPTION,
                7,
                null,
                null,
                CREATOR_ID,
                "!warn",
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

        when(eventService.getEventsSortedByIdInIncreasingOrder(CHAT_ID)).thenReturn(List.of(event));
        when(roleService.getRoleName(CHAT_ID, 7)).thenReturn(Optional.empty());

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = allEventsShowCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);
        verify(vkChatClient).sendText(sendMessageDto);

        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        String actual = captor.getValue().getText();

        assertTrue(actual.contains("роли с приоритетом 7"));
    }

    @Test
    void shouldShowCooldownDisabled() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});

        when(chatService.getChatTimeZone(CHAT_ID)).thenReturn(TimeZoneType.GMT_PLUS_3);
        when(eventService.getMaxEvents()).thenReturn(10);

        EventDto event = new EventDto(
                1L,
                MyEventType.SHORT_MESSAGE,
                null,
                MEMBER_ID,
                null,
                CREATOR_ID,
                "!ban",
                null,
                null,
                null,
                null,
                0,
                null,
                null,
                false,
                false,
                false
        );

        when(eventService.getEventsSortedByIdInIncreasingOrder(CHAT_ID)).thenReturn(List.of(event));
        when(globalUserService.getUserFullNameInRequiredCase(MEMBER_ID, NameCase.GENITIVE))
                .thenReturn(USER_NAME_GENITIVE);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = allEventsShowCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);

        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        String actual = captor.getValue().getText();

        assertTrue(actual.contains("⏳Кулдаун: отключён"));
    }

    @Test
    void shouldPropagateRuntimeExceptionFromEventService() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});

        when(chatService.getChatTimeZone(CHAT_ID)).thenReturn(TimeZoneType.GMT_PLUS_3);
        when(eventService.getEventsSortedByIdInIncreasingOrder(CHAT_ID))
                .thenThrow(new RuntimeException("Service error"));

        assertThrows(RuntimeException.class, () -> allEventsShowCommand.execute(commandMessage));
        verify(vkChatClient, never()).sendText(any());
        verify(globalUserService, never()).getUserFullNameInRequiredCase(anyLong(), any());
    }

    @Test
    void shouldPropagateApiExceptionFromVkChatClientSendText() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});

        when(chatService.getChatTimeZone(CHAT_ID)).thenReturn(TimeZoneType.GMT_PLUS_3);
        when(eventService.getEventsSortedByIdInIncreasingOrder(CHAT_ID)).thenReturn(List.of());
        when(eventService.getMaxEvents()).thenReturn(10);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        ApiException apiException = new ApiException(new Error().setErrorCode(1).setErrorMsg("API error"));
        doThrow(apiException).when(vkChatClient).sendText(any(SendMessageDto.class));

        assertThrows(ApiException.class, () -> allEventsShowCommand.execute(commandMessage));
        verify(vkChatClient).sendText(sendMessageDto);
    }

    @Test
    void shouldPropagateClientExceptionFromVkChatClientSendText() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});

        when(chatService.getChatTimeZone(CHAT_ID)).thenReturn(TimeZoneType.GMT_PLUS_3);
        when(eventService.getEventsSortedByIdInIncreasingOrder(CHAT_ID)).thenReturn(List.of());
        when(eventService.getMaxEvents()).thenReturn(10);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        ClientException clientException = new ClientException("VK client error");
        doThrow(clientException).when(vkChatClient).sendText(any(SendMessageDto.class));

        assertThrows(ClientException.class, () -> allEventsShowCommand.execute(commandMessage));
        verify(vkChatClient).sendText(sendMessageDto);
    }
}