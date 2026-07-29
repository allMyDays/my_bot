package com.example.my_bot.unit.command.commands.permission;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.commands.permission.AnyPermissionDeleteCommand;
import com.example.my_bot.command.commands.permission.MemberPermissionCreateCommand;
import com.example.my_bot.command.commands.permission.RolePermissionCreateCommand;
import com.example.my_bot.dto.RoleDto;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.command.CommandRoutingData;
import com.example.my_bot.dto.permission.MemberPermissionSettingResult;
import com.example.my_bot.dto.permission.RolePermissionSettingResult;
import com.example.my_bot.enumeration.command.CommandExecutionStatus;
import com.example.my_bot.enumeration.user.NameCase;
import com.example.my_bot.exception.command.CommandException;
import com.example.my_bot.exception.member.MemberException;
import com.example.my_bot.exception.permission.PermissionException;
import com.example.my_bot.exception.role.RoleException;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.resolver.UserInputResolver;
import com.example.my_bot.service.GlobalUserService;
import com.example.my_bot.service.chat.ChatService;
import com.example.my_bot.service.permission.MemberPermissionService;
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
class AnyPermissionDeleteCommandTest {

    private static final long CHAT_ID = 100L;
    private static final long FROM_ID = 200L;
    private static final long USER_ID = 300L;
    private static final String COMMAND_NAME = "ban";
    private static final String USER_NAME_ACCUSATIVE = "Ивана Иванова";

    @Mock
    private VkChatClient vkChatClient;

    @Mock
    private RolePermissionService rolePermissionService;

    @Mock
    private MemberPermissionService memberPermissionService;

    @Mock
    private UserInputResolver userInputResolver;

    @Mock
    private GlobalUserService userService;

    @Mock
    private MessageMapper messageMapper;

    @Mock
    private CommandMessageDto commandMessage;

    @Mock
    private CommandRoutingData commandRoutingData;

    @Mock
    private GroupActor groupActor;

    private AnyPermissionDeleteCommand anyPermissionDeleteCommand;

    @BeforeEach
    void setUp() {
        anyPermissionDeleteCommand = new AnyPermissionDeleteCommand(
                vkChatClient,
                rolePermissionService,
                memberPermissionService,
                userInputResolver,
                userService,
                messageMapper
        );

        when(commandMessage.getCommandRoutingData()).thenReturn(commandRoutingData);
        when(commandRoutingData.getDataBaseChatId()).thenReturn(CHAT_ID);
        when(commandMessage.getFromId()).thenReturn(FROM_ID);
    }

    @Test
    void shouldDeleteRolePermissionSuccess() throws ClientException, ApiException {
        // given
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{COMMAND_NAME});

        doNothing().when(rolePermissionService).deleteCustomRolePermission(CHAT_ID, COMMAND_NAME, FROM_ID);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        // when
        CommandExecutionStatus status = anyPermissionDeleteCommand.execute(commandMessage);

        // then
        assertEquals(CommandExecutionStatus.SUCCESS, status);
        verify(rolePermissionService).deleteCustomRolePermission(CHAT_ID, COMMAND_NAME, FROM_ID);
        verify(memberPermissionService, never()).deleteCustomMemberPermission(anyLong(), anyString(), anyLong(), anyLong());
        verify(vkChatClient).sendText(sendMessageDto);

        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        String actual = captor.getValue().getText();
        assertEquals("✅Настройка прав для указанной команды была сброшена до дефолтной роли.", actual);
    }

    @Test
    void shouldDeleteMemberPermissionSuccess() throws ClientException, ApiException {
        // given
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{COMMAND_NAME, "@user"});
        when(userInputResolver.getMemberIdByStringInput(CHAT_ID, "@user")).thenReturn(Optional.of(USER_ID));

        doNothing().when(memberPermissionService).deleteCustomMemberPermission(CHAT_ID, COMMAND_NAME, USER_ID, FROM_ID);

        when(userService.getUserFullNameInRequiredCase(USER_ID, NameCase.ACCUSATIVE))
                .thenReturn(USER_NAME_ACCUSATIVE);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class)) {
            textUtilsMock.when(() -> TextUtils.createMention(USER_ID)).thenReturn("@id300");

            // when
            CommandExecutionStatus status = anyPermissionDeleteCommand.execute(commandMessage);

            // then
            assertEquals(CommandExecutionStatus.SUCCESS, status);
            verify(memberPermissionService).deleteCustomMemberPermission(CHAT_ID, COMMAND_NAME, USER_ID, FROM_ID);
            verify(rolePermissionService, never()).deleteCustomRolePermission(anyLong(), anyString(), anyLong());
            verify(userService).getUserFullNameInRequiredCase(USER_ID, NameCase.ACCUSATIVE);
            verify(vkChatClient).sendText(sendMessageDto);

            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            String actual = captor.getValue().getText();
            assertEquals("✅Настройка сброшена. Теперь возможность использовать эту команду у @id300(Ивана Иванова) зависит только от уровня его роли.", actual);
        }
    }


    @Test
    void shouldReturnArgumentValidationErrorWhenNoArguments() throws ClientException, ApiException {
        // given
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        // when
        CommandExecutionStatus status = anyPermissionDeleteCommand.execute(commandMessage);

        // then
        assertEquals(CommandExecutionStatus.ARGUMENT_VALIDATION_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertTrue(captor.getValue().getText().contains("Вы ввели недостаточно аргументов для обработки этой команды."));
        verify(rolePermissionService, never()).deleteCustomRolePermission(anyLong(), anyString(), anyLong());
        verify(memberPermissionService, never()).deleteCustomMemberPermission(anyLong(), anyString(), anyLong(), anyLong());
    }

    @Test
    void shouldReturnArgumentValidationErrorWhenUserNotFound() throws ClientException, ApiException {
        // given
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{COMMAND_NAME, "@unknown"});
        when(userInputResolver.getMemberIdByStringInput(CHAT_ID, "@unknown")).thenReturn(Optional.empty());

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        // when
        CommandExecutionStatus status = anyPermissionDeleteCommand.execute(commandMessage);

        // then
        assertEquals(CommandExecutionStatus.ARGUMENT_VALIDATION_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertTrue(captor.getValue().getText().contains("Необходимо указать участника, к которому вы хотите применить эту команду."));
        verify(memberPermissionService, never()).deleteCustomMemberPermission(anyLong(), anyString(), anyLong(), anyLong());
    }

    @Test
    void shouldReturnBusinessLogicErrorOnCommandExceptionWhenDeletingRolePermission() throws ClientException, ApiException {
        // given
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{COMMAND_NAME});

        CommandException exception = new CommandException("Команда не найдена") {};
        doThrow(exception).when(rolePermissionService).deleteCustomRolePermission(CHAT_ID, COMMAND_NAME, FROM_ID);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        // when
        CommandExecutionStatus status = anyPermissionDeleteCommand.execute(commandMessage);

        // then
        assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertEquals(exception.getMessage(), captor.getValue().getText());
    }

    @Test
    void shouldReturnBusinessLogicErrorOnPermissionExceptionWhenDeletingMemberPermission() throws ClientException, ApiException {
        // given
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{COMMAND_NAME, "@user"});
        when(userInputResolver.getMemberIdByStringInput(CHAT_ID, "@user")).thenReturn(Optional.of(USER_ID));

        PermissionException exception = new PermissionException("Ошибка разрешений") {};
        doThrow(exception).when(memberPermissionService).deleteCustomMemberPermission(CHAT_ID, COMMAND_NAME, USER_ID, FROM_ID);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        // when
        CommandExecutionStatus status = anyPermissionDeleteCommand.execute(commandMessage);

        // then
        assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertEquals(exception.getMessage(), captor.getValue().getText());
    }

    @Test
    void shouldReturnBusinessLogicErrorOnMemberExceptionWhenDeletingMemberPermission() throws ClientException, ApiException {
        // given
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{COMMAND_NAME, "@user"});
        when(userInputResolver.getMemberIdByStringInput(CHAT_ID, "@user")).thenReturn(Optional.of(USER_ID));

        MemberException exception = new MemberException("Участник не найден") {};
        doThrow(exception).when(memberPermissionService).deleteCustomMemberPermission(CHAT_ID, COMMAND_NAME, USER_ID, FROM_ID);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        // when
        CommandExecutionStatus status = anyPermissionDeleteCommand.execute(commandMessage);

        // then
        assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertEquals(exception.getMessage(), captor.getValue().getText());
    }

    @Test
    void shouldPropagateClientExceptionFromSendText() throws ClientException, ApiException {
        // given
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{COMMAND_NAME});

        doNothing().when(rolePermissionService).deleteCustomRolePermission(CHAT_ID, COMMAND_NAME, FROM_ID);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        ClientException clientException = new ClientException("VK client error");
        doThrow(clientException).when(vkChatClient).sendText(any(SendMessageDto.class));

        // when / then
        assertThrows(ClientException.class, () -> anyPermissionDeleteCommand.execute(commandMessage));
        verify(vkChatClient).sendText(sendMessageDto);
    }

    @Test
    void shouldPropagateApiExceptionFromSendText() throws ClientException, ApiException {
        // given
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{COMMAND_NAME});

        doNothing().when(rolePermissionService).deleteCustomRolePermission(CHAT_ID, COMMAND_NAME, FROM_ID);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        ApiException apiException = new ApiException(new Error().setErrorCode(1).setErrorMsg("API error"));
        doThrow(apiException).when(vkChatClient).sendText(any(SendMessageDto.class));

        // when / then
        assertThrows(ApiException.class, () -> anyPermissionDeleteCommand.execute(commandMessage));
        verify(vkChatClient).sendText(sendMessageDto);
    }

    @Test
    void shouldPropagateRuntimeExceptionFromRolePermissionService() throws ClientException, ApiException {
        // given
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{COMMAND_NAME});

        RuntimeException runtimeException = new RuntimeException("Unexpected DB error");
        doThrow(runtimeException).when(rolePermissionService).deleteCustomRolePermission(CHAT_ID, COMMAND_NAME, FROM_ID);

        // when / then
        assertThrows(RuntimeException.class, () -> anyPermissionDeleteCommand.execute(commandMessage));
        verify(vkChatClient, never()).sendText(any());
    }

    @Test
    void shouldPropagateRuntimeExceptionFromMemberPermissionService() throws ClientException, ApiException {
        // given
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{COMMAND_NAME, "@user"});
        when(userInputResolver.getMemberIdByStringInput(CHAT_ID, "@user")).thenReturn(Optional.of(USER_ID));

        RuntimeException runtimeException = new RuntimeException("Unexpected DB error");
        doThrow(runtimeException).when(memberPermissionService).deleteCustomMemberPermission(CHAT_ID, COMMAND_NAME, USER_ID, FROM_ID);

        // when / then
        assertThrows(RuntimeException.class, () -> anyPermissionDeleteCommand.execute(commandMessage));
        verify(vkChatClient, never()).sendText(any());
    }

    @Test
    void shouldPropagateRuntimeExceptionFromUserService() throws ClientException, ApiException {
        // given
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{COMMAND_NAME, "@user"});
        when(userInputResolver.getMemberIdByStringInput(CHAT_ID, "@user")).thenReturn(Optional.of(USER_ID));

        doNothing().when(memberPermissionService).deleteCustomMemberPermission(CHAT_ID, COMMAND_NAME, USER_ID, FROM_ID);

        when(userService.getUserFullNameInRequiredCase(USER_ID, NameCase.ACCUSATIVE))
                .thenThrow(new RuntimeException("User service error"));

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        // when / then
        assertThrows(RuntimeException.class, () -> anyPermissionDeleteCommand.execute(commandMessage));
        verify(vkChatClient, never()).sendText(any());
    }
}
