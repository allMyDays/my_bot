package com.example.my_bot.unit.command.commands.limit;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.commands.limit.AllRateLimitsShowCommand;
import com.example.my_bot.command.commands.limit.RateLimitDeleteCommand;
import com.example.my_bot.command.commands.limit.RoleRateLimitCreateCommand;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.command.CommandRoutingData;
import com.example.my_bot.dto.limit.RoleRateLimitDto;
import com.example.my_bot.entity.RoleRateLimitEntity;
import com.example.my_bot.enumeration.command.CommandExecutionStatus;
import com.example.my_bot.exception.command.CommandException;
import com.example.my_bot.exception.limit.RateLimitException;
import com.example.my_bot.exception.role.RoleException;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoleRateLimitCreateCommandTest {

    private static final long CHAT_ID = 100L;
    private static final long FROM_ID = 200L;
    private static final String COMMAND_NAME = "!ping";
    private static final int MAX_USAGE = 3;
    private static final int ROLE_PRIORITY = 5;
    private static final String ROLE_NAME = "Модератор";
    private static final long TIME_PERIOD_SEC = 21600L; // 6 часов
    private static final String FORMATTED_DURATION = "6 часов";
    private static final boolean IS_PERSONAL = true;

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

    private RoleRateLimitCreateCommand roleRateLimitCreateCommand;

    @BeforeEach
    void setUp() {
        roleRateLimitCreateCommand = new RoleRateLimitCreateCommand(
                vkChatClient,
                roleRateLimitService,
                roleService,
                messageMapper
        );

        when(commandMessage.getCommandRoutingData()).thenReturn(commandRoutingData);
        when(commandRoutingData.getDataBaseChatId()).thenReturn(CHAT_ID);
        when(commandMessage.getFromId()).thenReturn(FROM_ID);
    }

    @Test
    void shouldCreateRateLimitWithRoleNameSuccess() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{COMMAND_NAME, "3", "6", "часов", ROLE_NAME});

        try (MockedStatic<TimeUtils> timeUtils = mockStatic(TimeUtils.class)) {
            timeUtils.when(() -> TimeUtils.toSecondsFromString("6", "часов"))
                    .thenReturn(Optional.of(TIME_PERIOD_SEC));
            timeUtils.when(() -> TimeUtils.formatDurationFromSeconds(TIME_PERIOD_SEC, true))
                    .thenReturn(FORMATTED_DURATION);

            RoleRateLimitEntity createdEntity = new RoleRateLimitEntity();
            createdEntity.setMaxUsage(MAX_USAGE);
            createdEntity.setTimePeriodSec((int) TIME_PERIOD_SEC);
            createdEntity.setCommandName(COMMAND_NAME);
            createdEntity.setRolePriority(ROLE_PRIORITY);
            createdEntity.setPersonal(false);

            when(roleRateLimitService.createCommandRateLimit(
                    eq(CHAT_ID), eq(FROM_ID), eq(COMMAND_NAME), eq(ROLE_NAME),
                    eq(MAX_USAGE), eq(TIME_PERIOD_SEC), eq(false)))
                    .thenReturn(createdEntity);

            when(roleService.getRoleName(CHAT_ID, ROLE_PRIORITY))
                    .thenReturn(Optional.of(ROLE_NAME));

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            CommandExecutionStatus status = roleRateLimitCreateCommand.execute(commandMessage);

            assertEquals(CommandExecutionStatus.SUCCESS, status);
            verify(roleRateLimitService).createCommandRateLimit(
                    CHAT_ID, FROM_ID, COMMAND_NAME, ROLE_NAME, MAX_USAGE, TIME_PERIOD_SEC, false);
            verify(roleService).getRoleName(CHAT_ID, ROLE_PRIORITY);
            verify(vkChatClient).sendText(sendMessageDto);

            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            String actual = captor.getValue().getText();
            String expected = "✅Успешно добавлен новый лимит в 3 использований за 6 часов для команды «!ping», воздействующий только на роль «Модератор»." +
                    "\n❗Вы не указали параметр «личный», поэтому лимит будет общим на всех участников с указанной ролью.";
            assertEquals(expected, actual);
        }
    }

    @Test
    void shouldCreateRateLimitWithRolePrioritySuccess() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{COMMAND_NAME, "3", "6", "часов", "5"});

        try (MockedStatic<TimeUtils> timeUtils = mockStatic(TimeUtils.class)) {
            timeUtils.when(() -> TimeUtils.toSecondsFromString("6", "часов"))
                    .thenReturn(Optional.of(TIME_PERIOD_SEC));
            timeUtils.when(() -> TimeUtils.formatDurationFromSeconds(TIME_PERIOD_SEC, true))
                    .thenReturn(FORMATTED_DURATION);

            RoleRateLimitEntity createdEntity = new RoleRateLimitEntity();
            createdEntity.setMaxUsage(MAX_USAGE);
            createdEntity.setTimePeriodSec((int) TIME_PERIOD_SEC);
            createdEntity.setCommandName(COMMAND_NAME);
            createdEntity.setRolePriority(ROLE_PRIORITY);
            createdEntity.setPersonal(false);

            when(roleRateLimitService.createCommandRateLimit(
                    eq(CHAT_ID), eq(FROM_ID), eq(COMMAND_NAME), eq(ROLE_PRIORITY),
                    eq(MAX_USAGE), eq(TIME_PERIOD_SEC), eq(false)))
                    .thenReturn(createdEntity);

            when(roleService.getRoleName(CHAT_ID, ROLE_PRIORITY))
                    .thenReturn(Optional.of(ROLE_NAME));

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            CommandExecutionStatus status = roleRateLimitCreateCommand.execute(commandMessage);

            assertEquals(CommandExecutionStatus.SUCCESS, status);
            verify(roleRateLimitService).createCommandRateLimit(
                    CHAT_ID, FROM_ID, COMMAND_NAME, ROLE_PRIORITY, MAX_USAGE, TIME_PERIOD_SEC, false);
            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            assertTrue(captor.getValue().getText().contains("воздействующий только на роль «Модератор»."));
        }
    }

    @Test
    void shouldCreateRateLimitWithPersonalFlagSuccess() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{COMMAND_NAME, "3", "6", "часов", ROLE_NAME, "личный"});

        try (MockedStatic<TimeUtils> timeUtils = mockStatic(TimeUtils.class)) {
            timeUtils.when(() -> TimeUtils.toSecondsFromString("6", "часов"))
                    .thenReturn(Optional.of(TIME_PERIOD_SEC));
            timeUtils.when(() -> TimeUtils.formatDurationFromSeconds(TIME_PERIOD_SEC, true))
                    .thenReturn(FORMATTED_DURATION);

            RoleRateLimitEntity createdEntity = new RoleRateLimitEntity();
            createdEntity.setMaxUsage(MAX_USAGE);
            createdEntity.setTimePeriodSec((int) TIME_PERIOD_SEC);
            createdEntity.setCommandName(COMMAND_NAME);
            createdEntity.setRolePriority(ROLE_PRIORITY);
            createdEntity.setPersonal(true);

            when(roleRateLimitService.createCommandRateLimit(
                    eq(CHAT_ID), eq(FROM_ID), eq(COMMAND_NAME), eq(ROLE_NAME),
                    eq(MAX_USAGE), eq(TIME_PERIOD_SEC), eq(true)))
                    .thenReturn(createdEntity);

            when(roleService.getRoleName(CHAT_ID, ROLE_PRIORITY))
                    .thenReturn(Optional.of(ROLE_NAME));

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            CommandExecutionStatus status = roleRateLimitCreateCommand.execute(commandMessage);

            assertEquals(CommandExecutionStatus.SUCCESS, status);
            verify(roleRateLimitService).createCommandRateLimit(
                    CHAT_ID, FROM_ID, COMMAND_NAME, ROLE_NAME, MAX_USAGE, TIME_PERIOD_SEC, true);
            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            assertTrue(captor.getValue().getText().contains("❗Лимит будет считаться индивидуально на каждого участника с указанной ролью."));
        }
    }

    @Test
    void shouldReturnArgumentValidationErrorWhenNotEnoughArguments() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{COMMAND_NAME, "3", "6", "часов"}); // 4 аргумента, нужно минимум 5

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = roleRateLimitCreateCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.ARGUMENT_VALIDATION_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        System.out.println(captor.getValue().getText());
        assertTrue(captor.getValue().getText().contains("Вы ввели недостаточно аргументов для обработки этой команды."));
        verify(roleRateLimitService, never()).createCommandRateLimit(anyLong(), anyLong(), anyString(), any(), anyInt(), anyLong(), anyBoolean());
    }

    @Test
    void shouldReturnArgumentValidationErrorWhenInvalidMaxUsage() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{COMMAND_NAME, "abc", "6", "часов", ROLE_NAME});

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = roleRateLimitCreateCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.ARGUMENT_VALIDATION_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        System.out.println(captor.getValue().getText());
        assertTrue(captor.getValue().getText().contains("Указанный вами аргумент не является корректным числовым значением."));
        verify(roleRateLimitService, never()).createCommandRateLimit(anyLong(), anyLong(), anyString(), any(), anyInt(), anyLong(), anyBoolean());
    }

    @Test
    void shouldReturnArgumentValidationErrorWhenInvalidRolePriorityNumber() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{COMMAND_NAME, "3", "6", "часов", "abc"});

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = roleRateLimitCreateCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.ARGUMENT_VALIDATION_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertTrue(captor.getValue().getText().contains("Некорректный целочисленный аргумент"));
        verify(roleRateLimitService, never()).createCommandRateLimit(anyLong(), anyLong(), anyString(), any(), anyInt(), anyLong(), anyBoolean());
    }

    @Test
    void shouldReturnArgumentValidationErrorWhenInvalidTimePeriod() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{COMMAND_NAME, "3", "invalid", "часов", ROLE_NAME});

        try (MockedStatic<TimeUtils> timeUtils = mockStatic(TimeUtils.class)) {
            timeUtils.when(() -> TimeUtils.toSecondsFromString("invalid", "часов"))
                    .thenReturn(Optional.empty());

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            CommandExecutionStatus status = roleRateLimitCreateCommand.execute(commandMessage);

            assertEquals(CommandExecutionStatus.ARGUMENT_VALIDATION_ERROR, status);
            verify(vkChatClient).sendText(sendMessageDto);
            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            System.out.println(captor.getValue().getText());
            assertTrue(captor.getValue().getText().contains("Указанный вами аргумент не является корректным временным периодом. Правильный пример: 2 минуты / 3 часа / 1 день"));
            verify(roleRateLimitService, never()).createCommandRateLimit(anyLong(), anyLong(), anyString(), any(), anyInt(), anyLong(), anyBoolean());
        }
    }

    @Test
    void shouldReturnBusinessLogicErrorOnRateLimitException() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{COMMAND_NAME, "3", "6", "часов", ROLE_NAME});

        try (MockedStatic<TimeUtils> timeUtils = mockStatic(TimeUtils.class)) {
            timeUtils.when(() -> TimeUtils.toSecondsFromString("6", "часов"))
                    .thenReturn(Optional.of(TIME_PERIOD_SEC));

            RateLimitException exception = new RateLimitException("Ошибка лимита") {};
            when(roleRateLimitService.createCommandRateLimit(
                    eq(CHAT_ID), eq(FROM_ID), eq(COMMAND_NAME), eq(ROLE_NAME),
                    eq(MAX_USAGE), eq(TIME_PERIOD_SEC), eq(false)))
                    .thenThrow(exception);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            CommandExecutionStatus status = roleRateLimitCreateCommand.execute(commandMessage);

            assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
            verify(vkChatClient).sendText(sendMessageDto);
            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            assertEquals(exception.getMessage(), captor.getValue().getText());
        }
    }

    @Test
    void shouldReturnBusinessLogicErrorOnRoleException() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{COMMAND_NAME, "3", "6", "часов", ROLE_NAME});

        try (MockedStatic<TimeUtils> timeUtils = mockStatic(TimeUtils.class)) {
            timeUtils.when(() -> TimeUtils.toSecondsFromString("6", "часов"))
                    .thenReturn(Optional.of(TIME_PERIOD_SEC));

            RoleException exception = new RoleException("Роль не найдена") {};
            when(roleRateLimitService.createCommandRateLimit(
                    eq(CHAT_ID), eq(FROM_ID), eq(COMMAND_NAME), eq(ROLE_NAME),
                    eq(MAX_USAGE), eq(TIME_PERIOD_SEC), eq(false)))
                    .thenThrow(exception);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            CommandExecutionStatus status = roleRateLimitCreateCommand.execute(commandMessage);

            assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
            verify(vkChatClient).sendText(sendMessageDto);
            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            assertEquals(exception.getMessage(), captor.getValue().getText());
        }
    }

    @Test
    void shouldReturnBusinessLogicErrorOnCommandException() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{COMMAND_NAME, "3", "6", "часов", ROLE_NAME});

        try (MockedStatic<TimeUtils> timeUtils = mockStatic(TimeUtils.class)) {
            timeUtils.when(() -> TimeUtils.toSecondsFromString("6", "часов"))
                    .thenReturn(Optional.of(TIME_PERIOD_SEC));

            CommandException exception = new CommandException("Ошибка команды") {};
            when(roleRateLimitService.createCommandRateLimit(
                    eq(CHAT_ID), eq(FROM_ID), eq(COMMAND_NAME), eq(ROLE_NAME),
                    eq(MAX_USAGE), eq(TIME_PERIOD_SEC), eq(false)))
                    .thenThrow(exception);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            CommandExecutionStatus status = roleRateLimitCreateCommand.execute(commandMessage);

            assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
            verify(vkChatClient).sendText(sendMessageDto);
            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            assertEquals(exception.getMessage(), captor.getValue().getText());
        }
    }

    @Test
    void shouldPropagateClientExceptionFromSendText() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{COMMAND_NAME, "3", "6", "часов", ROLE_NAME});

        try (MockedStatic<TimeUtils> timeUtils = mockStatic(TimeUtils.class)) {
            timeUtils.when(() -> TimeUtils.toSecondsFromString("6", "часов"))
                    .thenReturn(Optional.of(TIME_PERIOD_SEC));
            timeUtils.when(() -> TimeUtils.formatDurationFromSeconds(TIME_PERIOD_SEC, true))
                    .thenReturn(FORMATTED_DURATION);

            RoleRateLimitEntity createdEntity = new RoleRateLimitEntity();
            createdEntity.setMaxUsage(MAX_USAGE);
            createdEntity.setTimePeriodSec((int) TIME_PERIOD_SEC);
            createdEntity.setCommandName(COMMAND_NAME);
            createdEntity.setRolePriority(ROLE_PRIORITY);
            createdEntity.setPersonal(false);

            when(roleRateLimitService.createCommandRateLimit(
                    eq(CHAT_ID), eq(FROM_ID), eq(COMMAND_NAME), eq(ROLE_NAME),
                    eq(MAX_USAGE), eq(TIME_PERIOD_SEC), eq(false)))
                    .thenReturn(createdEntity);

            when(roleService.getRoleName(CHAT_ID, ROLE_PRIORITY))
                    .thenReturn(Optional.of(ROLE_NAME));

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            ClientException clientException = new ClientException("VK client error");
            doThrow(clientException).when(vkChatClient).sendText(any(SendMessageDto.class));

            assertThrows(ClientException.class, () -> roleRateLimitCreateCommand.execute(commandMessage));
            verify(vkChatClient).sendText(sendMessageDto);
        }
    }

    @Test
    void shouldPropagateApiExceptionFromSendText() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{COMMAND_NAME, "3", "6", "часов", ROLE_NAME});

        try (MockedStatic<TimeUtils> timeUtils = mockStatic(TimeUtils.class)) {
            timeUtils.when(() -> TimeUtils.toSecondsFromString("6", "часов"))
                    .thenReturn(Optional.of(TIME_PERIOD_SEC));
            timeUtils.when(() -> TimeUtils.formatDurationFromSeconds(TIME_PERIOD_SEC, true))
                    .thenReturn(FORMATTED_DURATION);

            RoleRateLimitEntity createdEntity = new RoleRateLimitEntity();
            createdEntity.setMaxUsage(MAX_USAGE);
            createdEntity.setTimePeriodSec((int) TIME_PERIOD_SEC);
            createdEntity.setCommandName(COMMAND_NAME);
            createdEntity.setRolePriority(ROLE_PRIORITY);
            createdEntity.setPersonal(false);

            when(roleRateLimitService.createCommandRateLimit(
                    eq(CHAT_ID), eq(FROM_ID), eq(COMMAND_NAME), eq(ROLE_NAME),
                    eq(MAX_USAGE), eq(TIME_PERIOD_SEC), eq(false)))
                    .thenReturn(createdEntity);

            when(roleService.getRoleName(CHAT_ID, ROLE_PRIORITY))
                    .thenReturn(Optional.of(ROLE_NAME));

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            ApiException apiException = new ApiException(new Error().setErrorCode(1).setErrorMsg("API error"));
            doThrow(apiException).when(vkChatClient).sendText(any(SendMessageDto.class));

            assertThrows(ApiException.class, () -> roleRateLimitCreateCommand.execute(commandMessage));
            verify(vkChatClient).sendText(sendMessageDto);
        }
    }

    @Test
    void shouldPropagateRuntimeExceptionFromGetRoleName() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{COMMAND_NAME, "3", "6", "часов", ROLE_NAME});

        try (MockedStatic<TimeUtils> timeUtils = mockStatic(TimeUtils.class)) {
            timeUtils.when(() -> TimeUtils.toSecondsFromString("6", "часов"))
                    .thenReturn(Optional.of(TIME_PERIOD_SEC));
            timeUtils.when(() -> TimeUtils.formatDurationFromSeconds(TIME_PERIOD_SEC, true))
                    .thenReturn(FORMATTED_DURATION);

            RoleRateLimitEntity createdEntity = new RoleRateLimitEntity();
            createdEntity.setMaxUsage(MAX_USAGE);
            createdEntity.setTimePeriodSec((int) TIME_PERIOD_SEC);
            createdEntity.setCommandName(COMMAND_NAME);
            createdEntity.setRolePriority(ROLE_PRIORITY);
            createdEntity.setPersonal(false);

            when(roleRateLimitService.createCommandRateLimit(
                    eq(CHAT_ID), eq(FROM_ID), eq(COMMAND_NAME), eq(ROLE_NAME),
                    eq(MAX_USAGE), eq(TIME_PERIOD_SEC), eq(false)))
                    .thenReturn(createdEntity);

            when(roleService.getRoleName(CHAT_ID, ROLE_PRIORITY))
                    .thenThrow(new RuntimeException("Role service error"));

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            assertThrows(RuntimeException.class, () -> roleRateLimitCreateCommand.execute(commandMessage));
            verify(vkChatClient, never()).sendText(any());
        }
    }

    @Test
    void shouldPropagateRuntimeExceptionFromCreateRateLimit() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{COMMAND_NAME, "3", "6", "часов", ROLE_NAME});

        try (MockedStatic<TimeUtils> timeUtils = mockStatic(TimeUtils.class)) {
            timeUtils.when(() -> TimeUtils.toSecondsFromString("6", "часов"))
                    .thenReturn(Optional.of(TIME_PERIOD_SEC));

            when(roleRateLimitService.createCommandRateLimit(
                    eq(CHAT_ID), eq(FROM_ID), eq(COMMAND_NAME), eq(ROLE_NAME),
                    eq(MAX_USAGE), eq(TIME_PERIOD_SEC), eq(false)))
                    .thenThrow(new RuntimeException("Unexpected error"));

            assertThrows(RuntimeException.class, () -> roleRateLimitCreateCommand.execute(commandMessage));
            verify(vkChatClient, never()).sendText(any());
        }
    }
}
