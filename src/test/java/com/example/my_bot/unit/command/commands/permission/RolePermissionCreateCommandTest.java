package com.example.my_bot.unit.command.commands.permission;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.commands.permission.RolePermissionCreateCommand;
import com.example.my_bot.dto.RoleDto;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.command.CommandRoutingData;
import com.example.my_bot.dto.permission.RolePermissionSettingResult;
import com.example.my_bot.enumeration.command.CommandExecutionStatus;
import com.example.my_bot.exception.command.CommandException;
import com.example.my_bot.exception.permission.PermissionException;
import com.example.my_bot.exception.role.RoleException;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.service.chat.ChatService;
import com.example.my_bot.service.permission.RolePermissionService;
import com.example.my_bot.utils.TextUtils;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RolePermissionCreateCommandTest {

    private static final long CHAT_ID = 100L;
    private static final long FROM_ID = 200L;
    private static final int ROLE_PRIORITY = 5;
    private static final String ROLE_NAME = "Модератор";
    private static final char CHAT_PREFIX = '!';
    private static final String COMMAND1 = "ban";
    private static final String COMMAND2 = "kick";

    @Mock
    private VkChatClient vkChatClient;

    @Mock
    private RolePermissionService permissionService;

    @Mock
    private ChatService chatService;

    @Mock
    private MessageMapper messageMapper;

    @Mock
    private CommandMessageDto commandMessage;

    @Mock
    private CommandRoutingData commandRoutingData;

    @Mock
    private GroupActor groupActor;

    private RolePermissionCreateCommand rolePermissionCreateCommand;

    @BeforeEach
    void setUp() {
        rolePermissionCreateCommand = new RolePermissionCreateCommand(
                vkChatClient,
                permissionService,
                chatService,
                messageMapper
        );

        when(commandMessage.getCommandRoutingData()).thenReturn(commandRoutingData);
        when(commandRoutingData.getDataBaseChatId()).thenReturn(CHAT_ID);
        when(commandMessage.getFromId()).thenReturn(FROM_ID);
    }

    @Test
    void shouldAllowCommandsForRoleByPrioritySuccess() throws ClientException, ApiException {
        // given
        String[] args = {"5", "ban", "kick"};
        when(commandMessage.getFirstRowArguments()).thenReturn(args);
        when(commandMessage.getAllRows()).thenReturn(args);

        try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class)) {
            textUtilsMock.when(() -> TextUtils.isNumber("5")).thenReturn(true);
            textUtilsMock.when(() -> TextUtils.isValidInteger("5")).thenReturn(true);

            Set<String> commands = Set.of("ban", "kick");
            RolePermissionSettingResult result = new RolePermissionSettingResult();
            result.setRoleDto(new RoleDto(ROLE_NAME, ROLE_PRIORITY));
            result.setAccepted(Set.of("ban", "kick"));
            result.setHasRequiredPermissionAlready(Set.of());
            result.setForbiddenToEdit(Set.of());
            result.setNotEnoughSpaceToAddNew(Set.of());
            result.setNotFound(Set.of());

            when(permissionService.allowCommandForRole(eq(CHAT_ID), eq(FROM_ID), eq(commands), eq(ROLE_PRIORITY)))
                    .thenReturn(result);

            when(chatService.getChatPrefix(CHAT_ID)).thenReturn(Optional.of(CHAT_PREFIX));

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class)))
                    .thenReturn(sendMessageDto);

            // when
            CommandExecutionStatus status = rolePermissionCreateCommand.execute(commandMessage);

            // then
            assertEquals(CommandExecutionStatus.SUCCESS, status);
            verify(permissionService).allowCommandForRole(CHAT_ID, FROM_ID, commands, ROLE_PRIORITY);
            verify(chatService).getChatPrefix(CHAT_ID);
            verify(vkChatClient).sendText(sendMessageDto);

            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            String actual = captor.getValue().getText();

            String expected = "⚙ !ban\n⚙ !kick\n\n✅Команды:\n%1$s\n\nТеперь могут применяться только участниками с ролью «Модератор» и выше.".formatted(
                    "⚙ !ban\n⚙ !kick"
            );
            assertTrue(actual.contains("✅Команды:"));
            assertTrue(actual.contains("⚙ !ban"));
            assertTrue(actual.contains("⚙ !kick"));
            assertTrue(actual.contains("Модератор"));
        }
    }

    @Test
    void shouldAllowCommandsForRoleByNameSuccess() throws ClientException, ApiException {
        // given
        String[] args = {"Модератор", "ban", "kick"};
        when(commandMessage.getFirstRowArguments()).thenReturn(args);
        when(commandMessage.getAllRows()).thenReturn(args);

        try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class)) {
            textUtilsMock.when(() -> TextUtils.isNumber("Модератор")).thenReturn(false);

            Set<String> commands = Set.of("ban", "kick");
            RolePermissionSettingResult result = new RolePermissionSettingResult();
            result.setRoleDto(new RoleDto(ROLE_NAME, ROLE_PRIORITY));
            result.setAccepted(Set.of("ban", "kick"));

            when(permissionService.allowCommandForRole(eq(CHAT_ID), eq(FROM_ID), eq(commands), eq(ROLE_NAME)))
                    .thenReturn(result);

            when(chatService.getChatPrefix(CHAT_ID)).thenReturn(Optional.of(CHAT_PREFIX));

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class)))
                    .thenReturn(sendMessageDto);

            // when
            CommandExecutionStatus status = rolePermissionCreateCommand.execute(commandMessage);

            // then
            assertEquals(CommandExecutionStatus.SUCCESS, status);
            verify(permissionService).allowCommandForRole(CHAT_ID, FROM_ID, commands, ROLE_NAME);
        }
    }

    @Test
    void shouldHandleMultipleSections() throws ClientException, ApiException {
        // given
        String[] args = {"5", "ban", "kick", "mute"};
        when(commandMessage.getFirstRowArguments()).thenReturn(args);
        when(commandMessage.getAllRows()).thenReturn(args);

        try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class)) {
            textUtilsMock.when(() -> TextUtils.isNumber("5")).thenReturn(true);
            textUtilsMock.when(() -> TextUtils.isValidInteger("5")).thenReturn(true);

            Set<String> commands = Set.of("ban", "kick", "mute");
            RolePermissionSettingResult result = new RolePermissionSettingResult();
            result.setRoleDto(new RoleDto(ROLE_NAME, ROLE_PRIORITY));
            result.setAccepted(Set.of("ban"));
            result.setHasRequiredPermissionAlready(Set.of("kick"));
            result.setForbiddenToEdit(Set.of("mute"));

            when(permissionService.allowCommandForRole(eq(CHAT_ID), eq(FROM_ID), eq(commands), eq(ROLE_PRIORITY)))
                    .thenReturn(result);

            when(chatService.getChatPrefix(CHAT_ID)).thenReturn(Optional.of(CHAT_PREFIX));

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class)))
                    .thenReturn(sendMessageDto);

            // when
            CommandExecutionStatus status = rolePermissionCreateCommand.execute(commandMessage);

            // then
            assertEquals(CommandExecutionStatus.SUCCESS, status);
            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            String actual = captor.getValue().getText();

            assertTrue(actual.contains("✅Команды:"));
            assertTrue(actual.contains("⚙ !ban"));
            assertTrue(actual.contains("‼Команды:"));
            assertTrue(actual.contains("⚙ !kick"));
            assertTrue(actual.contains("🚫Команды:"));
            assertTrue(actual.contains("⚙ !mute"));
        }
    }

    @Test
    void shouldReturnArgumentValidationErrorWhenNotEnoughArguments() throws ClientException, ApiException {
        // given
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"5"}); // только приоритет

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        // when
        CommandExecutionStatus status = rolePermissionCreateCommand.execute(commandMessage);

        // then
        assertEquals(CommandExecutionStatus.ARGUMENT_VALIDATION_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        System.out.println(captor.getValue().getText());
        assertTrue(captor.getValue().getText().contains("Вы ввели недостаточно аргументов для обработки этой команды."));
        verify(permissionService, never()).allowCommandForRole(anyLong(), anyLong(), anySet(), anyInt());
        verify(permissionService, never()).allowCommandForRole(anyLong(), anyLong(), anySet(), anyString());
    }

    @Test
    void shouldReturnArgumentValidationErrorWhenInvalidNumber() throws ClientException, ApiException {
        // given
        String[] args = {"12.5", "ban"};
        when(commandMessage.getFirstRowArguments()).thenReturn(args);

        try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class)) {
            textUtilsMock.when(() -> TextUtils.isNumber("12.5")).thenReturn(true);
            textUtilsMock.when(() -> TextUtils.isValidInteger("12.5")).thenReturn(false);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class)))
                    .thenReturn(sendMessageDto);

            // when
            CommandExecutionStatus status = rolePermissionCreateCommand.execute(commandMessage);

            // then
            assertEquals(CommandExecutionStatus.ARGUMENT_VALIDATION_ERROR, status);
            verify(vkChatClient).sendText(sendMessageDto);
            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            System.out.println(captor.getValue().getText());
            assertTrue(captor.getValue().getText().contains("Указанный вами аргумент не является корректным числовым значением."));
            verify(permissionService, never()).allowCommandForRole(anyLong(), anyLong(), anySet(), anyInt());
            verify(permissionService, never()).allowCommandForRole(anyLong(), anyLong(), anySet(), anyString());
        }
    }

    @Test
    void shouldReturnBusinessLogicErrorOnPermissionException() throws ClientException, ApiException {
        // given
        String[] args = {"5", "ban"};
        when(commandMessage.getFirstRowArguments()).thenReturn(args);
        when(commandMessage.getAllRows()).thenReturn(args);

        try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class)) {
            textUtilsMock.when(() -> TextUtils.isNumber("5")).thenReturn(true);
            textUtilsMock.when(() -> TextUtils.isValidInteger("5")).thenReturn(true);

            PermissionException exception = new PermissionException("Ошибка разрешений") {};
            when(permissionService.allowCommandForRole(eq(CHAT_ID), eq(FROM_ID), anySet(), eq(ROLE_PRIORITY)))
                    .thenThrow(exception);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class)))
                    .thenReturn(sendMessageDto);

            // when
            CommandExecutionStatus status = rolePermissionCreateCommand.execute(commandMessage);

            // then
            assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
            verify(vkChatClient).sendText(sendMessageDto);
            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            assertEquals(exception.getMessage(), captor.getValue().getText());
        }
    }

    @Test
    void shouldReturnBusinessLogicErrorOnRoleException() throws ClientException, ApiException {
        // given
        String[] args = {"5", "ban"};
        when(commandMessage.getFirstRowArguments()).thenReturn(args);
        when(commandMessage.getAllRows()).thenReturn(args);

        try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class)) {
            textUtilsMock.when(() -> TextUtils.isNumber("5")).thenReturn(true);
            textUtilsMock.when(() -> TextUtils.isValidInteger("5")).thenReturn(true);

            RoleException exception = new RoleException("Роль не найдена") {};
            when(permissionService.allowCommandForRole(eq(CHAT_ID), eq(FROM_ID), anySet(), eq(ROLE_PRIORITY)))
                    .thenThrow(exception);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class)))
                    .thenReturn(sendMessageDto);

            // when
            CommandExecutionStatus status = rolePermissionCreateCommand.execute(commandMessage);

            // then
            assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
            verify(vkChatClient).sendText(sendMessageDto);
            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            assertEquals(exception.getMessage(), captor.getValue().getText());
        }
    }

    @Test
    void shouldReturnBusinessLogicErrorOnCommandException() throws ClientException, ApiException {
        // given
        String[] args = {"5", "ban"};
        when(commandMessage.getFirstRowArguments()).thenReturn(args);
        when(commandMessage.getAllRows()).thenReturn(args);

        try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class)) {
            textUtilsMock.when(() -> TextUtils.isNumber("5")).thenReturn(true);
            textUtilsMock.when(() -> TextUtils.isValidInteger("5")).thenReturn(true);

            CommandException exception = new CommandException("Команда не найдена") {};
            when(permissionService.allowCommandForRole(eq(CHAT_ID), eq(FROM_ID), anySet(), eq(ROLE_PRIORITY)))
                    .thenThrow(exception);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class)))
                    .thenReturn(sendMessageDto);

            // when
            CommandExecutionStatus status = rolePermissionCreateCommand.execute(commandMessage);

            // then
            assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
            verify(vkChatClient).sendText(sendMessageDto);
            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            assertEquals(exception.getMessage(), captor.getValue().getText());
        }
    }

    // ==================== Проброс исключений от VkChatClient ====================

    @Test
    void shouldPropagateClientExceptionFromSendText() throws ClientException, ApiException {
        // given
        String[] args = {"5", "ban"};
        when(commandMessage.getFirstRowArguments()).thenReturn(args);
        when(commandMessage.getAllRows()).thenReturn(args);

        try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class)) {
            textUtilsMock.when(() -> TextUtils.isNumber("5")).thenReturn(true);
            textUtilsMock.when(() -> TextUtils.isValidInteger("5")).thenReturn(true);

            RolePermissionSettingResult result = new RolePermissionSettingResult();
            result.setRoleDto(new RoleDto(ROLE_NAME, ROLE_PRIORITY));
            result.setAccepted(Set.of("ban"));
            when(permissionService.allowCommandForRole(eq(CHAT_ID), eq(FROM_ID), anySet(), eq(ROLE_PRIORITY)))
                    .thenReturn(result);
            when(chatService.getChatPrefix(CHAT_ID)).thenReturn(Optional.of(CHAT_PREFIX));

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class)))
                    .thenReturn(sendMessageDto);

            ClientException clientException = new ClientException("VK client error");
            doThrow(clientException).when(vkChatClient).sendText(any(SendMessageDto.class));

            // when / then
            assertThrows(ClientException.class, () -> rolePermissionCreateCommand.execute(commandMessage));
            verify(vkChatClient).sendText(sendMessageDto);
        }
    }

    @Test
    void shouldPropagateApiExceptionFromSendText() throws ClientException, ApiException {
        // given
        String[] args = {"5", "ban"};
        when(commandMessage.getFirstRowArguments()).thenReturn(args);
        when(commandMessage.getAllRows()).thenReturn(args);

        try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class)) {
            textUtilsMock.when(() -> TextUtils.isNumber("5")).thenReturn(true);
            textUtilsMock.when(() -> TextUtils.isValidInteger("5")).thenReturn(true);

            RolePermissionSettingResult result = new RolePermissionSettingResult();
            result.setRoleDto(new RoleDto(ROLE_NAME, ROLE_PRIORITY));
            result.setAccepted(Set.of("ban"));
            when(permissionService.allowCommandForRole(eq(CHAT_ID), eq(FROM_ID), anySet(), eq(ROLE_PRIORITY)))
                    .thenReturn(result);
            when(chatService.getChatPrefix(CHAT_ID)).thenReturn(Optional.of(CHAT_PREFIX));

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class)))
                    .thenReturn(sendMessageDto);

            ApiException apiException = new ApiException(new Error().setErrorCode(1).setErrorMsg("API error"));
            doThrow(apiException).when(vkChatClient).sendText(any(SendMessageDto.class));

            // when / then
            assertThrows(ApiException.class, () -> rolePermissionCreateCommand.execute(commandMessage));
            verify(vkChatClient).sendText(sendMessageDto);
        }
    }

    @Test
    void shouldPropagateRuntimeExceptionFromPermissionService() throws ClientException, ApiException {
        // given
        String[] args = {"5", "ban"};
        when(commandMessage.getFirstRowArguments()).thenReturn(args);
        when(commandMessage.getAllRows()).thenReturn(args);

        try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class)) {
            textUtilsMock.when(() -> TextUtils.isNumber("5")).thenReturn(true);
            textUtilsMock.when(() -> TextUtils.isValidInteger("5")).thenReturn(true);

            when(permissionService.allowCommandForRole(eq(CHAT_ID), eq(FROM_ID), anySet(), eq(ROLE_PRIORITY)))
                    .thenThrow(new RuntimeException("Unexpected error"));

            // when / then
            assertThrows(RuntimeException.class, () -> rolePermissionCreateCommand.execute(commandMessage));
            verify(vkChatClient, never()).sendText(any());
        }
    }

    @Test
    void shouldPropagateRuntimeExceptionFromChatService() throws ClientException, ApiException {
        // given
        String[] args = {"5", "ban"};
        when(commandMessage.getFirstRowArguments()).thenReturn(args);
        when(commandMessage.getAllRows()).thenReturn(args);

        try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class)) {
            textUtilsMock.when(() -> TextUtils.isNumber("5")).thenReturn(true);
            textUtilsMock.when(() -> TextUtils.isValidInteger("5")).thenReturn(true);

            RolePermissionSettingResult result = new RolePermissionSettingResult();
            result.setRoleDto(new RoleDto(ROLE_NAME, ROLE_PRIORITY));
            result.setAccepted(Set.of("ban"));
            when(permissionService.allowCommandForRole(eq(CHAT_ID), eq(FROM_ID), anySet(), eq(ROLE_PRIORITY)))
                    .thenReturn(result);

            when(chatService.getChatPrefix(CHAT_ID)).thenThrow(new RuntimeException("Chat service error"));

            // when / then
            assertThrows(RuntimeException.class, () -> rolePermissionCreateCommand.execute(commandMessage));
            verify(vkChatClient, never()).sendText(any());
        }
    }
}