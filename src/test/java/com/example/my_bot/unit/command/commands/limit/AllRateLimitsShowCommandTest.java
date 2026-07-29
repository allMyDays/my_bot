package com.example.my_bot.unit.command.commands.limit;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.commands.limit.AllRateLimitsShowCommand;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.command.CommandRoutingData;
import com.example.my_bot.dto.limit.RoleRateLimitDto;
import com.example.my_bot.enumeration.command.CommandExecutionStatus;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.service.RoleRateLimitService;
import com.example.my_bot.service.RoleService;
import com.example.my_bot.utils.TimeUtils;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.objects.base.Error;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AllRateLimitsShowCommandTest {

    private static final long CHAT_ID = 100L;
    private static final String COMMAND_NAME = "!ban";
    private static final int ROLE_PRIORITY = 5;
    private static final String ROLE_NAME = "Модератор";
    private static final int MAX_USAGE = 10;
    private static final int TIME_PERIOD_SEC = 60;
    private static final String FORMATTED_DURATION = "1 минута";

    @Mock
    private VkChatClient vkChatClient;

    @Mock
    private RoleRateLimitService roleRateLimitService;

    @Mock
    private RoleService roleService;

    @Mock
    private MessageMapper messageMapper;

    @Mock
    private CommandMessageDto commandMessage;

    @Mock
    private CommandRoutingData commandRoutingData;

    @Mock
    private GroupActor groupActor;

    private AllRateLimitsShowCommand allRateLimitsShowCommand;

    @BeforeEach
    void setUp() {
        allRateLimitsShowCommand = new AllRateLimitsShowCommand(
                vkChatClient,
                roleRateLimitService,
                roleService,
                messageMapper
        );

        when(commandMessage.getCommandRoutingData()).thenReturn(commandRoutingData);
        when(commandRoutingData.getDataBaseChatId()).thenReturn(CHAT_ID);
    }

    @Test
    void shouldShowRateLimitsSuccess() throws ClientException, ApiException {
        RoleRateLimitDto limit1 = new RoleRateLimitDto(
                1L, COMMAND_NAME, ROLE_PRIORITY, false, MAX_USAGE, TIME_PERIOD_SEC
        );
        RoleRateLimitDto limit2 = new RoleRateLimitDto(
                2L, "!kick", 3, true, 5, 120
        );
        List<RoleRateLimitDto> limits = List.of(limit1, limit2);

        when(roleRateLimitService.getRoleLimitsSortedByEntityId(CHAT_ID)).thenReturn(limits);
        when(roleService.getAllRolesWithNoSorting(CHAT_ID)).thenReturn(Map.of(ROLE_PRIORITY, ROLE_NAME));

        try (MockedStatic<TimeUtils> timeUtils = mockStatic(TimeUtils.class)) {
            timeUtils.when(() -> TimeUtils.formatDurationFromSeconds(TIME_PERIOD_SEC, true))
                    .thenReturn(FORMATTED_DURATION);
            timeUtils.when(() -> TimeUtils.formatDurationFromSeconds(120, true))
                    .thenReturn("2 минуты");

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(anyString(), any(CommandMessageDto.class)))
                    .thenReturn(sendMessageDto);

            CommandExecutionStatus status = allRateLimitsShowCommand.execute(commandMessage);

            assertEquals(CommandExecutionStatus.SUCCESS, status);
            verify(roleRateLimitService).getRoleLimitsSortedByEntityId(CHAT_ID);
            verify(roleService).getAllRolesWithNoSorting(CHAT_ID);
            verify(vkChatClient).sendText(sendMessageDto);

            ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
            verify(messageMapper).toSendMessageDto(textCaptor.capture(), any(CommandMessageDto.class));
            String actual = textCaptor.getValue();

            assertTrue(actual.contains("В чате установлено 2 пользовательских временных лимитов для команд."));
            assertTrue(actual.contains("1. Лимит команды «!ban» в [10] использований за 1 минута для роли «Модератор». Лимит общий для всех участников с этой ролью."));
            assertTrue(actual.contains("2. Лимит команды «!kick» в [5] использований за 2 минуты для роли с приоритетом 3. Считается отдельно для каждого участника."));
        }
    }

    @Test
    void shouldShowRateLimitsWithFallbackRolePriority() throws ClientException, ApiException {
        RoleRateLimitDto limit = new RoleRateLimitDto(
                1L, COMMAND_NAME, ROLE_PRIORITY, false, MAX_USAGE, TIME_PERIOD_SEC
        );
        List<RoleRateLimitDto> limits = List.of(limit);

        when(roleRateLimitService.getRoleLimitsSortedByEntityId(CHAT_ID)).thenReturn(limits);
        when(roleService.getAllRolesWithNoSorting(CHAT_ID)).thenReturn(Map.of());

        try (MockedStatic<TimeUtils> timeUtils = mockStatic(TimeUtils.class)) {
            timeUtils.when(() -> TimeUtils.formatDurationFromSeconds(TIME_PERIOD_SEC, true))
                    .thenReturn(FORMATTED_DURATION);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(anyString(), any(CommandMessageDto.class)))
                    .thenReturn(sendMessageDto);

            CommandExecutionStatus status = allRateLimitsShowCommand.execute(commandMessage);

            assertEquals(CommandExecutionStatus.SUCCESS, status);
            ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
            verify(messageMapper).toSendMessageDto(textCaptor.capture(), any(CommandMessageDto.class));
            String actual = textCaptor.getValue();

            assertTrue(actual.contains("Лимит команды «!ban» в [10] использований за 1 минута для роли с приоритетом 5."));
        }
    }

    @Test
    void shouldShowEmptyList() throws ClientException, ApiException {
        when(roleRateLimitService.getRoleLimitsSortedByEntityId(CHAT_ID)).thenReturn(List.of());
        when(roleService.getAllRolesWithNoSorting(CHAT_ID)).thenReturn(Map.of());

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(anyString(), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        CommandExecutionStatus status = allRateLimitsShowCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);
        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(messageMapper).toSendMessageDto(textCaptor.capture(), any(CommandMessageDto.class));
        String actual = textCaptor.getValue();

        assertTrue(actual.contains("В чате установлено 0 пользовательских временных лимитов для команд."));
        assertFalse(actual.contains("1."));
    }

    @Test
    void shouldPropagateRuntimeExceptionFromRoleRateLimitService() throws ClientException, ApiException {
        when(roleRateLimitService.getRoleLimitsSortedByEntityId(CHAT_ID))
                .thenThrow(new RuntimeException("DB error"));

        assertThrows(RuntimeException.class, () -> allRateLimitsShowCommand.execute(commandMessage));
        verify(vkChatClient, never()).sendText(any());
        verify(messageMapper, never()).toSendMessageDto(anyString(), any(CommandMessageDto.class));
    }

    @Test
    void shouldPropagateRuntimeExceptionFromRoleService() throws ClientException, ApiException {
        when(roleRateLimitService.getRoleLimitsSortedByEntityId(CHAT_ID)).thenReturn(List.of());
        when(roleService.getAllRolesWithNoSorting(CHAT_ID))
                .thenThrow(new RuntimeException("Role service error"));

        assertThrows(RuntimeException.class, () -> allRateLimitsShowCommand.execute(commandMessage));
        verify(vkChatClient, never()).sendText(any());
        verify(messageMapper, never()).toSendMessageDto(anyString(), any(CommandMessageDto.class));
    }

    @Test
    void shouldPropagateClientExceptionFromSendText() throws ClientException, ApiException {
        RoleRateLimitDto limit = new RoleRateLimitDto(
                1L, COMMAND_NAME, ROLE_PRIORITY, false, MAX_USAGE, TIME_PERIOD_SEC
        );
        when(roleRateLimitService.getRoleLimitsSortedByEntityId(CHAT_ID)).thenReturn(List.of(limit));
        when(roleService.getAllRolesWithNoSorting(CHAT_ID)).thenReturn(Map.of(ROLE_PRIORITY, ROLE_NAME));

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(anyString(), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        ClientException clientException = new ClientException("VK client error");
        doThrow(clientException).when(vkChatClient).sendText(any(SendMessageDto.class));

        assertThrows(ClientException.class, () -> allRateLimitsShowCommand.execute(commandMessage));
        verify(vkChatClient).sendText(sendMessageDto);
    }

    @Test
    void shouldPropagateApiExceptionFromSendText() throws ClientException, ApiException {
        RoleRateLimitDto limit = new RoleRateLimitDto(
                1L, COMMAND_NAME, ROLE_PRIORITY, false, MAX_USAGE, TIME_PERIOD_SEC
        );
        when(roleRateLimitService.getRoleLimitsSortedByEntityId(CHAT_ID)).thenReturn(List.of(limit));
        when(roleService.getAllRolesWithNoSorting(CHAT_ID)).thenReturn(Map.of(ROLE_PRIORITY, ROLE_NAME));

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(anyString(), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        ApiException apiException = new ApiException(new Error().setErrorCode(1).setErrorMsg("API error"));
        doThrow(apiException).when(vkChatClient).sendText(any(SendMessageDto.class));

        assertThrows(ApiException.class, () -> allRateLimitsShowCommand.execute(commandMessage));
        verify(vkChatClient).sendText(sendMessageDto);
    }
}