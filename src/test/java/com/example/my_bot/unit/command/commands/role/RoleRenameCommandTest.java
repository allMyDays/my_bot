package com.example.my_bot.unit.command.commands.role;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.commands.role.AllRolesShowCommand;
import com.example.my_bot.command.commands.role.RoleCreateCommand;
import com.example.my_bot.command.commands.role.RoleDeleteCommand;
import com.example.my_bot.command.commands.role.RoleRenameCommand;
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
class RoleRenameCommandTest {

    private static final long CHAT_ID = 100L;
    private static final long FROM_ID = 200L;
    private static final int ROLE_PRIORITY = 5;
    private static final String OLD_ROLE_NAME = "Модератор";
    private static final String NEW_ROLE_NAME = "Супермодератор";

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

    private RoleRenameCommand roleRenameCommand;

    @BeforeEach
    void setUp() {
        roleRenameCommand = new RoleRenameCommand(vkChatClient, roleService, messageMapper);

        when(commandMessage.getCommandRoutingData()).thenReturn(commandRoutingData);
        when(commandRoutingData.getDataBaseChatId()).thenReturn(CHAT_ID);
        when(commandMessage.getFromId()).thenReturn(FROM_ID);
    }

    @Test
    void shouldRenameRoleByPrioritySuccess() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{"5", "Супермодератор"});

        try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class)) {
            textUtilsMock.when(() -> TextUtils.isNumber("5")).thenReturn(true);
            textUtilsMock.when(() -> TextUtils.isValidInteger("5")).thenReturn(true);

            RoleDto editedRole = new RoleDto(NEW_ROLE_NAME, ROLE_PRIORITY);
            when(roleService.renameRole(eq(CHAT_ID), eq(FROM_ID), eq(ROLE_PRIORITY), eq(NEW_ROLE_NAME)))
                    .thenReturn(editedRole);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class)))
                    .thenReturn(sendMessageDto);

            CommandExecutionStatus status = roleRenameCommand.execute(commandMessage);

            assertEquals(CommandExecutionStatus.SUCCESS, status);
            verify(roleService).renameRole(CHAT_ID, FROM_ID, ROLE_PRIORITY, NEW_ROLE_NAME);
            verify(vkChatClient).sendText(sendMessageDto);

            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            String actual = captor.getValue().getText();
            assertEquals("✅Вы успешно переименовали указанную роль с приоритетом 5 в «Супермодератор».", actual);
        }
    }

    @Test
    void shouldRenameRoleByNameSuccess() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{"Модератор", "Супермодератор"});

        try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class)) {
            textUtilsMock.when(() -> TextUtils.isNumber("Модератор")).thenReturn(false);

            RoleDto editedRole = new RoleDto(NEW_ROLE_NAME, ROLE_PRIORITY);
            when(roleService.renameRole(eq(CHAT_ID), eq(FROM_ID), eq(OLD_ROLE_NAME), eq(NEW_ROLE_NAME)))
                    .thenReturn(editedRole);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class)))
                    .thenReturn(sendMessageDto);

            CommandExecutionStatus status = roleRenameCommand.execute(commandMessage);

            assertEquals(CommandExecutionStatus.SUCCESS, status);
            verify(roleService).renameRole(CHAT_ID, FROM_ID, OLD_ROLE_NAME, NEW_ROLE_NAME);
            verify(vkChatClient).sendText(sendMessageDto);

            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            String actual = captor.getValue().getText();
            assertEquals("✅Вы успешно переименовали указанную роль с приоритетом 5 в «Супермодератор».", actual);
        }
    }

    @Test
    void shouldReturnArgumentValidationErrorWhenNotEnoughArguments() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"5"}); // только приоритет, без нового имени

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        CommandExecutionStatus status = roleRenameCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.ARGUMENT_VALIDATION_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        System.out.println(captor.getValue().getText());
        assertTrue(captor.getValue().getText().contains("Вы ввели недостаточно аргументов для обработки этой команды."));
        verify(roleService, never()).renameRole(anyLong(), anyLong(), anyInt(), anyString());
        verify(roleService, never()).renameRole(anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    void shouldReturnArgumentValidationErrorWhenInvalidNumber() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{"12.5", "Новое имя"});

        try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class)) {
            textUtilsMock.when(() -> TextUtils.isNumber("12.5")).thenReturn(true);
            textUtilsMock.when(() -> TextUtils.isValidInteger("12.5")).thenReturn(false);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class)))
                    .thenReturn(sendMessageDto);

            CommandExecutionStatus status = roleRenameCommand.execute(commandMessage);

            assertEquals(CommandExecutionStatus.ARGUMENT_VALIDATION_ERROR, status);
            verify(vkChatClient).sendText(sendMessageDto);
            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            System.out.println(captor.getValue().getText());
            assertTrue(captor.getValue().getText().contains("Указанный вами аргумент не является корректным числовым значением."));
            verify(roleService, never()).renameRole(anyLong(), anyLong(), anyInt(), anyString());
            verify(roleService, never()).renameRole(anyLong(), anyLong(), anyString(), anyString());
        }
    }

    @Test
    void shouldReturnBusinessLogicErrorOnRoleException() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{"5", "Супермодератор"});

        try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class)) {
            textUtilsMock.when(() -> TextUtils.isNumber("5")).thenReturn(true);
            textUtilsMock.when(() -> TextUtils.isValidInteger("5")).thenReturn(true);

            RoleException roleException = new RoleException("Роль не найдена") {};
            when(roleService.renameRole(eq(CHAT_ID), eq(FROM_ID), eq(ROLE_PRIORITY), eq(NEW_ROLE_NAME)))
                    .thenThrow(roleException);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class)))
                    .thenReturn(sendMessageDto);

            CommandExecutionStatus status = roleRenameCommand.execute(commandMessage);

            assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
            verify(vkChatClient).sendText(sendMessageDto);
            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            assertEquals(roleException.getMessage(), captor.getValue().getText());
        }
    }

    @Test
    void shouldPropagateClientExceptionFromSendText() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{"5", "Супермодератор"});

        try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class)) {
            textUtilsMock.when(() -> TextUtils.isNumber("5")).thenReturn(true);
            textUtilsMock.when(() -> TextUtils.isValidInteger("5")).thenReturn(true);

            RoleDto editedRole = new RoleDto(NEW_ROLE_NAME, ROLE_PRIORITY);
            when(roleService.renameRole(eq(CHAT_ID), eq(FROM_ID), eq(ROLE_PRIORITY), eq(NEW_ROLE_NAME)))
                    .thenReturn(editedRole);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class)))
                    .thenReturn(sendMessageDto);

            ClientException clientException = new ClientException("VK client error");
            doThrow(clientException).when(vkChatClient).sendText(any(SendMessageDto.class));

            assertThrows(ClientException.class, () -> roleRenameCommand.execute(commandMessage));
            verify(vkChatClient).sendText(sendMessageDto);
        }
    }

    @Test
    void shouldPropagateApiExceptionFromSendText() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{"5", "Супермодератор"});

        try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class)) {
            textUtilsMock.when(() -> TextUtils.isNumber("5")).thenReturn(true);
            textUtilsMock.when(() -> TextUtils.isValidInteger("5")).thenReturn(true);

            RoleDto editedRole = new RoleDto(NEW_ROLE_NAME, ROLE_PRIORITY);
            when(roleService.renameRole(eq(CHAT_ID), eq(FROM_ID), eq(ROLE_PRIORITY), eq(NEW_ROLE_NAME)))
                    .thenReturn(editedRole);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class)))
                    .thenReturn(sendMessageDto);

            ApiException apiException = new ApiException(new Error().setErrorCode(1).setErrorMsg("API error"));
            doThrow(apiException).when(vkChatClient).sendText(any(SendMessageDto.class));

            assertThrows(ApiException.class, () -> roleRenameCommand.execute(commandMessage));
            verify(vkChatClient).sendText(sendMessageDto);
        }
    }


    @Test
    void shouldPropagateRuntimeExceptionFromRoleService() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments())
                .thenReturn(new String[]{"5", "Супермодератор"});

        try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class)) {
            textUtilsMock.when(() -> TextUtils.isNumber("5")).thenReturn(true);
            textUtilsMock.when(() -> TextUtils.isValidInteger("5")).thenReturn(true);

            when(roleService.renameRole(eq(CHAT_ID), eq(FROM_ID), eq(ROLE_PRIORITY), eq(NEW_ROLE_NAME)))
                    .thenThrow(new RuntimeException("Unexpected DB error"));

            assertThrows(RuntimeException.class, () -> roleRenameCommand.execute(commandMessage));
            verify(vkChatClient, never()).sendText(any());
        }
    }
}