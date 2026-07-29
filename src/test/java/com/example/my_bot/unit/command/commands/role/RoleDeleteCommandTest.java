package com.example.my_bot.unit.command.commands.role;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.commands.role.AllRolesShowCommand;
import com.example.my_bot.command.commands.role.RoleCreateCommand;
import com.example.my_bot.command.commands.role.RoleDeleteCommand;
import com.example.my_bot.dto.RoleDto;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.command.CommandRoutingData;
import com.example.my_bot.entity.RoleEntity;
import com.example.my_bot.enumeration.DefaultRole;
import com.example.my_bot.enumeration.command.CommandExecutionStatus;
import com.example.my_bot.exception.role.RoleException;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.service.RoleService;
import com.example.my_bot.utils.TextUtils;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.objects.base.Error;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.TreeMap;

import static com.example.my_bot.enumeration.DefaultRole.isDefaultRole;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RoleDeleteCommandTest {

    private static final long CHAT_ID = 100L;
    private static final long FROM_ID = 200L;
    private static final int ROLE_PRIORITY = 5;
    private static final String ROLE_NAME = "Модератор";
    private static final int FALLBACK_PRIORITY = 1;
    private static final String FALLBACK_ROLE_NAME = "Участник";

    @Mock
    private VkChatClient vkChatClient;

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

    private RoleDeleteCommand roleDeleteCommand;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        roleDeleteCommand = new RoleDeleteCommand(vkChatClient, roleService, messageMapper);

        when(commandMessage.getCommandRoutingData()).thenReturn(commandRoutingData);
        when(commandRoutingData.getDataBaseChatId()).thenReturn(CHAT_ID);
        when(commandMessage.getFromId()).thenReturn(FROM_ID);
    }

    @Test
    void shouldDeleteRoleByPrioritySuccess() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"5"});

        try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class)) {
            textUtilsMock.when(() -> TextUtils.isNumber("5")).thenReturn(true);
            textUtilsMock.when(() -> TextUtils.isValidInteger("5")).thenReturn(true);

            RoleDto fallbackRole = new RoleDto(FALLBACK_ROLE_NAME, FALLBACK_PRIORITY);
            when(roleService.deleteCustomRole(eq(CHAT_ID), eq(FROM_ID), eq(ROLE_PRIORITY)))
                    .thenReturn(fallbackRole);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class)))
                    .thenReturn(sendMessageDto);

            CommandExecutionStatus status = roleDeleteCommand.execute(commandMessage);

            assertEquals(CommandExecutionStatus.SUCCESS, status);
            verify(roleService).deleteCustomRole(CHAT_ID, FROM_ID, ROLE_PRIORITY);
            verify(vkChatClient).sendText(sendMessageDto);

            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            String actual = captor.getValue().getText();
            assertEquals("✅Вы успешно удалили указанную роль. Все участники с этой ролью автоматически получили роль «Участник» с приоритетом 1.", actual);
        }
    }

    @Test
    void shouldDeleteRoleByNameSuccess() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"Модератор"});

        try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class)) {
            textUtilsMock.when(() -> TextUtils.isNumber("Модератор")).thenReturn(false);

            RoleDto fallbackRole = new RoleDto(FALLBACK_ROLE_NAME, FALLBACK_PRIORITY);
            when(roleService.deleteCustomRole(eq(CHAT_ID), eq(FROM_ID), eq(ROLE_NAME)))
                    .thenReturn(fallbackRole);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class)))
                    .thenReturn(sendMessageDto);

            CommandExecutionStatus status = roleDeleteCommand.execute(commandMessage);

            assertEquals(CommandExecutionStatus.SUCCESS, status);
            verify(roleService).deleteCustomRole(CHAT_ID, FROM_ID, ROLE_NAME);
            verify(vkChatClient).sendText(sendMessageDto);

            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            String actual = captor.getValue().getText();
            assertEquals("✅Вы успешно удалили указанную роль. Все участники с этой ролью автоматически получили роль «Участник» с приоритетом 1.", actual);
        }
    }

    @Test
    void shouldDeleteRoleByNameWithSpacesSuccess() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"Старший", "Модератор"});

        try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class)) {
            textUtilsMock.when(() -> TextUtils.isNumber("Старший")).thenReturn(false);

            String fullName = "Старший Модератор";
            RoleDto fallbackRole = new RoleDto(FALLBACK_ROLE_NAME, FALLBACK_PRIORITY);
            when(roleService.deleteCustomRole(eq(CHAT_ID), eq(FROM_ID), eq(fullName)))
                    .thenReturn(fallbackRole);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class)))
                    .thenReturn(sendMessageDto);

            CommandExecutionStatus status = roleDeleteCommand.execute(commandMessage);

            assertEquals(CommandExecutionStatus.SUCCESS, status);
            verify(roleService).deleteCustomRole(CHAT_ID, FROM_ID, fullName);
            verify(vkChatClient).sendText(sendMessageDto);
        }
    }

    @Test
    void shouldReturnArgumentValidationErrorWhenNoArguments() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        CommandExecutionStatus status = roleDeleteCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.ARGUMENT_VALIDATION_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertTrue(captor.getValue().getText().contains("Вы ввели недостаточно аргументов для обработки этой команды."));
        verify(roleService, never()).deleteCustomRole(anyLong(), anyLong(), anyInt());
        verify(roleService, never()).deleteCustomRole(anyLong(), anyLong(), anyString());
    }

    @Test
    void shouldReturnArgumentValidationErrorWhenInvalidNumber() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"12.5"});

        try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class)) {
            textUtilsMock.when(() -> TextUtils.isNumber("12.5")).thenReturn(true);
            textUtilsMock.when(() -> TextUtils.isValidInteger("12.5")).thenReturn(false);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class)))
                    .thenReturn(sendMessageDto);

            CommandExecutionStatus status = roleDeleteCommand.execute(commandMessage);

            assertEquals(CommandExecutionStatus.ARGUMENT_VALIDATION_ERROR, status);
            verify(vkChatClient).sendText(sendMessageDto);
            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            assertTrue(captor.getValue().getText().contains("Указанный вами аргумент не является корректным числовым значением."));
            verify(roleService, never()).deleteCustomRole(anyLong(), anyLong(), anyInt());
            verify(roleService, never()).deleteCustomRole(anyLong(), anyLong(), anyString());
        }
    }

    @Test
    void shouldReturnBusinessLogicErrorOnRoleException() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"5"});

        try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class)) {
            textUtilsMock.when(() -> TextUtils.isNumber("5")).thenReturn(true);
            textUtilsMock.when(() -> TextUtils.isValidInteger("5")).thenReturn(true);

            RoleException roleException = new RoleException("Роль не найдена") {};
            when(roleService.deleteCustomRole(eq(CHAT_ID), eq(FROM_ID), eq(ROLE_PRIORITY)))
                    .thenThrow(roleException);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class)))
                    .thenReturn(sendMessageDto);

            CommandExecutionStatus status = roleDeleteCommand.execute(commandMessage);

            assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
            verify(vkChatClient).sendText(sendMessageDto);
            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            assertEquals(roleException.getMessage(), captor.getValue().getText());
        }
    }

    @Test
    void shouldPropagateClientExceptionFromSendText() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"5"});

        try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class)) {
            textUtilsMock.when(() -> TextUtils.isNumber("5")).thenReturn(true);
            textUtilsMock.when(() -> TextUtils.isValidInteger("5")).thenReturn(true);

            RoleDto fallbackRole = new RoleDto(FALLBACK_ROLE_NAME, FALLBACK_PRIORITY);
            when(roleService.deleteCustomRole(eq(CHAT_ID), eq(FROM_ID), eq(ROLE_PRIORITY)))
                    .thenReturn(fallbackRole);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class)))
                    .thenReturn(sendMessageDto);

            ClientException clientException = new ClientException("VK client error");
            doThrow(clientException).when(vkChatClient).sendText(any(SendMessageDto.class));

            assertThrows(ClientException.class, () -> roleDeleteCommand.execute(commandMessage));
            verify(vkChatClient).sendText(sendMessageDto);
        }
    }

    @Test
    void shouldPropagateApiExceptionFromSendText() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"5"});

        try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class)) {
            textUtilsMock.when(() -> TextUtils.isNumber("5")).thenReturn(true);
            textUtilsMock.when(() -> TextUtils.isValidInteger("5")).thenReturn(true);

            RoleDto fallbackRole = new RoleDto(FALLBACK_ROLE_NAME, FALLBACK_PRIORITY);
            when(roleService.deleteCustomRole(eq(CHAT_ID), eq(FROM_ID), eq(ROLE_PRIORITY)))
                    .thenReturn(fallbackRole);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class)))
                    .thenReturn(sendMessageDto);

            ApiException apiException = new ApiException(new Error().setErrorCode(1).setErrorMsg("API error"));
            doThrow(apiException).when(vkChatClient).sendText(any(SendMessageDto.class));

            assertThrows(ApiException.class, () -> roleDeleteCommand.execute(commandMessage));
            verify(vkChatClient).sendText(sendMessageDto);
        }
    }

    @Test
    void shouldPropagateRuntimeExceptionFromRoleService() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"5"});

        try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class)) {
            textUtilsMock.when(() -> TextUtils.isNumber("5")).thenReturn(true);
            textUtilsMock.when(() -> TextUtils.isValidInteger("5")).thenReturn(true);

            when(roleService.deleteCustomRole(eq(CHAT_ID), eq(FROM_ID), eq(ROLE_PRIORITY)))
                    .thenThrow(new RuntimeException("Unexpected DB error"));

            assertThrows(RuntimeException.class, () -> roleDeleteCommand.execute(commandMessage));
            verify(vkChatClient, never()).sendText(any());
        }
    }
}

