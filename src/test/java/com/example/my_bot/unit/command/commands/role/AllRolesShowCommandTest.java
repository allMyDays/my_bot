package com.example.my_bot.unit.command.commands.role;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.commands.role.AllRolesShowCommand;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.command.CommandRoutingData;
import com.example.my_bot.enumeration.DefaultRole;
import com.example.my_bot.enumeration.command.CommandExecutionStatus;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.service.RoleService;
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
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.TreeMap;

import static com.example.my_bot.enumeration.DefaultRole.isDefaultRole;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AllRolesShowCommandTest {

    private static final long CHAT_ID = 100L;

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

    private AllRolesShowCommand allRolesShowCommand;

    @BeforeEach
    void setUp() {
        allRolesShowCommand = new AllRolesShowCommand(messageMapper);
        allRolesShowCommand.setVkChatClient(vkChatClient, roleService);

        when(commandMessage.getCommandRoutingData()).thenReturn(commandRoutingData);
        when(commandRoutingData.getDataBaseChatId()).thenReturn(CHAT_ID);
    }

    @Test
    void shouldShowAllRolesSuccess() throws ClientException, ApiException {
        TreeMap<Integer, String> roles = new TreeMap<>((a, b) -> b - a);
        roles.put(5, "Модератор");
        roles.put(3, "Помощник");
        roles.put(1, "Участник");

        when(roleService.getAllRolesSortedInDescendingOrder(CHAT_ID)).thenReturn(roles);

        try (MockedStatic<DefaultRole> defaultRoleMock = mockStatic(DefaultRole.class)) {
            defaultRoleMock.when(() -> DefaultRole.isDefaultRole(5)).thenReturn(false);
            defaultRoleMock.when(() -> DefaultRole.isDefaultRole(3)).thenReturn(false);
            defaultRoleMock.when(() -> DefaultRole.isDefaultRole(1)).thenReturn(true);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(anyString(), any(CommandMessageDto.class)))
                    .thenReturn(sendMessageDto);

            CommandExecutionStatus status = allRolesShowCommand.execute(commandMessage);

            assertEquals(CommandExecutionStatus.SUCCESS, status);
            verify(roleService).getAllRolesSortedInDescendingOrder(CHAT_ID);
            verify(vkChatClient).sendText(sendMessageDto);

            ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
            verify(messageMapper).toSendMessageDto(textCaptor.capture(), any(CommandMessageDto.class));
            String actual = textCaptor.getValue();

            assertTrue(actual.contains("В чате 2 дополнительных ролей. Вот полный список:"));
            assertTrue(actual.contains("Модератор — 5"));
            assertTrue(actual.contains("Помощник — 3"));
            assertTrue(actual.contains("Участник — 1"));
        }
    }

    @Test
    void shouldShowNoRoles() throws ClientException, ApiException {
        TreeMap<Integer, String> roles = new TreeMap<>();
        when(roleService.getAllRolesSortedInDescendingOrder(CHAT_ID)).thenReturn(roles);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(anyString(), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        CommandExecutionStatus status = allRolesShowCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);
        verify(roleService).getAllRolesSortedInDescendingOrder(CHAT_ID);
        verify(vkChatClient).sendText(sendMessageDto);

        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(messageMapper).toSendMessageDto(textCaptor.capture(), any(CommandMessageDto.class));
        String actual = textCaptor.getValue();

        assertTrue(actual.contains("В чате 0 дополнительных ролей. Вот полный список:"));
        assertFalse(actual.contains("—"));
    }

    @Test
    void shouldCountOnlyNonDefaultRoles() throws ClientException, ApiException {
        TreeMap<Integer, String> roles = new TreeMap<>((a, b) -> b - a);
        roles.put(10, "Администратор");
        roles.put(5, "Модератор");
        roles.put(1, "Участник");
        roles.put(0, "Бот");

        when(roleService.getAllRolesSortedInDescendingOrder(CHAT_ID)).thenReturn(roles);

        try (MockedStatic<DefaultRole> defaultRoleMock = mockStatic(DefaultRole.class)) {
            defaultRoleMock.when(() -> DefaultRole.isDefaultRole(10)).thenReturn(false);
            defaultRoleMock.when(() -> DefaultRole.isDefaultRole(5)).thenReturn(false);
            defaultRoleMock.when(() -> DefaultRole.isDefaultRole(1)).thenReturn(true);
            defaultRoleMock.when(() -> DefaultRole.isDefaultRole(0)).thenReturn(true);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(anyString(), any(CommandMessageDto.class)))
                    .thenReturn(sendMessageDto);

            CommandExecutionStatus status = allRolesShowCommand.execute(commandMessage);

            assertEquals(CommandExecutionStatus.SUCCESS, status);
            ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
            verify(messageMapper).toSendMessageDto(textCaptor.capture(), any(CommandMessageDto.class));
            String actual = textCaptor.getValue();

            assertTrue(actual.contains("В чате 2 дополнительных ролей. Вот полный список:"));
            assertTrue(actual.contains("Администратор — 10"));
            assertTrue(actual.contains("Модератор — 5"));
            assertTrue(actual.contains("Участник — 1"));
            assertTrue(actual.contains("Бот — 0"));
        }
    }

    @Test
    void shouldPropagateRuntimeExceptionFromRoleService() throws ClientException, ApiException {
        when(roleService.getAllRolesSortedInDescendingOrder(CHAT_ID))
                .thenThrow(new RuntimeException("DB error"));

        assertThrows(RuntimeException.class, () -> allRolesShowCommand.execute(commandMessage));
        verify(vkChatClient, never()).sendText(any());
        verify(messageMapper, never()).toSendMessageDto(anyString(), any(CommandMessageDto.class));
    }

    @Test
    void shouldPropagateClientExceptionFromSendText() throws ClientException, ApiException {
        TreeMap<Integer, String> roles = new TreeMap<>();
        when(roleService.getAllRolesSortedInDescendingOrder(CHAT_ID)).thenReturn(roles);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(anyString(), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        ClientException clientException = new ClientException("VK client error");
        doThrow(clientException).when(vkChatClient).sendText(any(SendMessageDto.class));

        assertThrows(ClientException.class, () -> allRolesShowCommand.execute(commandMessage));
        verify(vkChatClient).sendText(sendMessageDto);
    }

    @Test
    void shouldPropagateApiExceptionFromSendText() throws ClientException, ApiException {
        TreeMap<Integer, String> roles = new TreeMap<>();
        when(roleService.getAllRolesSortedInDescendingOrder(CHAT_ID)).thenReturn(roles);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(anyString(), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        ApiException apiException = new ApiException(new Error().setErrorCode(1).setErrorMsg("API error"));
        doThrow(apiException).when(vkChatClient).sendText(any(SendMessageDto.class));

        assertThrows(ApiException.class, () -> allRolesShowCommand.execute(commandMessage));
        verify(vkChatClient).sendText(sendMessageDto);
    }
}
