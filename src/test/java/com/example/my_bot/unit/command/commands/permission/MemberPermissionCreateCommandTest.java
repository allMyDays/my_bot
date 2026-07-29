package com.example.my_bot.unit.command.commands.permission;

import com.example.my_bot.client.VkChatClient;
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
class MemberPermissionCreateCommandTest {

    private static final long CHAT_ID = 100L;
    private static final long FROM_ID = 200L;
    private static final long TARGET_USER_ID = 300L;
    private static final char CHAT_PREFIX = '!';
    private static final String USER_NAME_INSTRUMENTAL = "Иваном Ивановым";
    private static final String MENTION = "@id300";

    @Mock
    private VkChatClient vkChatClient;

    @Mock
    private ChatService chatService;

    @Mock
    private UserInputResolver userInputResolver;

    @Mock
    private MemberPermissionService memberPermissionService;

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

    private MemberPermissionCreateCommand command;

    @BeforeEach
    void setUp() {
        command = new MemberPermissionCreateCommand(
                vkChatClient,
                chatService,
                userInputResolver,
                memberPermissionService,
                userService,
                messageMapper
        );

        when(commandMessage.getCommandRoutingData()).thenReturn(commandRoutingData);
        when(commandRoutingData.getDataBaseChatId()).thenReturn(CHAT_ID);
        when(commandMessage.getFromId()).thenReturn(FROM_ID);
    }

    @Test
    void shouldAllowCommandsForMemberSuccess() throws ClientException, ApiException {
        String[] args = {"@user", "ban", "kick"};
        when(commandMessage.getFirstRowArguments()).thenReturn(args);
        when(commandMessage.getAllRows()).thenReturn(args);
        when(userInputResolver.getMemberIdByStringInput(CHAT_ID, "@user")).thenReturn(Optional.of(TARGET_USER_ID));

        Set<String> commands = Set.of("ban", "kick");
        MemberPermissionSettingResult result = new MemberPermissionSettingResult();
        result.setAccepted(Set.of("ban", "kick"));
        result.setHasRequiredPermissionAlready(Set.of());
        result.setForbiddenToEdit(Set.of());
        result.setNotEnoughSpaceToAddNew(Set.of());
        result.setNotFound(Set.of());

        when(memberPermissionService.allowOrForbidCommandForMember(
                eq(CHAT_ID), eq(FROM_ID), eq(commands), eq(TARGET_USER_ID), eq(true)))
                .thenReturn(result);

        when(chatService.getChatPrefix(CHAT_ID)).thenReturn(Optional.of(CHAT_PREFIX));
        when(userService.getUserFullNameInRequiredCase(TARGET_USER_ID, NameCase.INSTRUMENTAL))
                .thenReturn(USER_NAME_INSTRUMENTAL);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class)) {
            textUtilsMock.when(() -> TextUtils.createMention(TARGET_USER_ID)).thenReturn(MENTION);

            CommandExecutionStatus status = command.execute(commandMessage);

            assertEquals(CommandExecutionStatus.SUCCESS, status);
            verify(memberPermissionService).allowOrForbidCommandForMember(CHAT_ID, FROM_ID, commands, TARGET_USER_ID, true);
            verify(chatService).getChatPrefix(CHAT_ID);
            verify(userService).getUserFullNameInRequiredCase(TARGET_USER_ID, NameCase.INSTRUMENTAL);
            verify(vkChatClient).sendText(sendMessageDto);

            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            String actual = captor.getValue().getText();

            assertTrue(actual.contains("✅Команды:"));
            assertTrue(actual.contains("⚙ !ban"));
            assertTrue(actual.contains("⚙ !kick"));
            assertTrue(actual.contains("Теперь могут персонально применяться @id300(Иваном Ивановым)"));
        }
    }

    @Test
    void shouldHandleMultipleCommandsAndSections() throws ClientException, ApiException {
        String[] args = {"@user", "ban", "kick", "mute"};
        when(commandMessage.getFirstRowArguments()).thenReturn(args);
        when(commandMessage.getAllRows()).thenReturn(args);
        when(userInputResolver.getMemberIdByStringInput(CHAT_ID, "@user")).thenReturn(Optional.of(TARGET_USER_ID));

        Set<String> commands = Set.of("ban", "kick", "mute");
        MemberPermissionSettingResult result = new MemberPermissionSettingResult();
        result.setAccepted(Set.of("ban"));
        result.setHasRequiredPermissionAlready(Set.of("kick"));
        result.setForbiddenToEdit(Set.of("mute"));

        when(memberPermissionService.allowOrForbidCommandForMember(
                eq(CHAT_ID), eq(FROM_ID), eq(commands), eq(TARGET_USER_ID), eq(true)))
                .thenReturn(result);

        when(chatService.getChatPrefix(CHAT_ID)).thenReturn(Optional.of(CHAT_PREFIX));
        when(userService.getUserFullNameInRequiredCase(TARGET_USER_ID, NameCase.INSTRUMENTAL))
                .thenReturn(USER_NAME_INSTRUMENTAL);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class)) {
            textUtilsMock.when(() -> TextUtils.createMention(TARGET_USER_ID)).thenReturn(MENTION);

            command.execute(commandMessage);

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
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"@user"}); // только пользователь

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        CommandExecutionStatus status = command.execute(commandMessage);

        assertEquals(CommandExecutionStatus.ARGUMENT_VALIDATION_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertTrue(captor.getValue().getText().contains("Вы ввели недостаточно аргументов для обработки этой команды."));
        verify(memberPermissionService, never()).allowOrForbidCommandForMember(anyLong(), anyLong(), anySet(), anyLong(), anyBoolean());
    }

    @Test
    void shouldReturnArgumentValidationErrorWhenUserNotFound() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"@unknown", "ban"});
        when(userInputResolver.getMemberIdByStringInput(CHAT_ID, "@unknown")).thenReturn(Optional.empty());

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        CommandExecutionStatus status = command.execute(commandMessage);

        assertEquals(CommandExecutionStatus.ARGUMENT_VALIDATION_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        System.out.println(captor.getValue().getText());
        assertTrue(captor.getValue().getText().contains("Не удалось получить участника по указанному вами строчному аргументу."));
        verify(memberPermissionService, never()).allowOrForbidCommandForMember(anyLong(), anyLong(), anySet(), anyLong(), anyBoolean());
    }

    @Test
    void shouldReturnBusinessLogicErrorOnPermissionException() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"@user", "ban"});
        when(commandMessage.getAllRows()).thenReturn(new String[]{"@user", "ban"});
        when(userInputResolver.getMemberIdByStringInput(CHAT_ID, "@user")).thenReturn(Optional.of(TARGET_USER_ID));

        PermissionException exception = new PermissionException("Ошибка разрешений") {};
        when(memberPermissionService.allowOrForbidCommandForMember(
                eq(CHAT_ID), eq(FROM_ID), anySet(), eq(TARGET_USER_ID), eq(true)))
                .thenThrow(exception);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        CommandExecutionStatus status = command.execute(commandMessage);

        assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertEquals(exception.getMessage(), captor.getValue().getText());
    }

    @Test
    void shouldReturnBusinessLogicErrorOnMemberException() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"@user", "ban"});
        when(commandMessage.getAllRows()).thenReturn(new String[]{"@user", "ban"});
        when(userInputResolver.getMemberIdByStringInput(CHAT_ID, "@user")).thenReturn(Optional.of(TARGET_USER_ID));

        MemberException exception = new MemberException("Участник не найден") {};
        when(memberPermissionService.allowOrForbidCommandForMember(
                eq(CHAT_ID), eq(FROM_ID), anySet(), eq(TARGET_USER_ID), eq(true)))
                .thenThrow(exception);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        CommandExecutionStatus status = command.execute(commandMessage);

        assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertEquals(exception.getMessage(), captor.getValue().getText());
    }

    @Test
    void shouldPropagateClientExceptionFromSendText() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"@user", "ban"});
        when(commandMessage.getAllRows()).thenReturn(new String[]{"@user", "ban"});
        when(userInputResolver.getMemberIdByStringInput(CHAT_ID, "@user")).thenReturn(Optional.of(TARGET_USER_ID));

        MemberPermissionSettingResult result = new MemberPermissionSettingResult();
        result.setAccepted(Set.of("ban"));
        when(memberPermissionService.allowOrForbidCommandForMember(
                eq(CHAT_ID), eq(FROM_ID), anySet(), eq(TARGET_USER_ID), eq(true)))
                .thenReturn(result);
        when(chatService.getChatPrefix(CHAT_ID)).thenReturn(Optional.of(CHAT_PREFIX));
        when(userService.getUserFullNameInRequiredCase(TARGET_USER_ID, NameCase.INSTRUMENTAL))
                .thenReturn(USER_NAME_INSTRUMENTAL);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        ClientException clientException = new ClientException("VK client error");
        doThrow(clientException).when(vkChatClient).sendText(any(SendMessageDto.class));

        try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class)) {
            textUtilsMock.when(() -> TextUtils.createMention(TARGET_USER_ID)).thenReturn(MENTION);

            assertThrows(ClientException.class, () -> command.execute(commandMessage));
            verify(vkChatClient).sendText(sendMessageDto);
        }
    }

    @Test
    void shouldPropagateApiExceptionFromSendText() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"@user", "ban"});
        when(commandMessage.getAllRows()).thenReturn(new String[]{"@user", "ban"});
        when(userInputResolver.getMemberIdByStringInput(CHAT_ID, "@user")).thenReturn(Optional.of(TARGET_USER_ID));

        MemberPermissionSettingResult result = new MemberPermissionSettingResult();
        result.setAccepted(Set.of("ban"));
        when(memberPermissionService.allowOrForbidCommandForMember(
                eq(CHAT_ID), eq(FROM_ID), anySet(), eq(TARGET_USER_ID), eq(true)))
                .thenReturn(result);
        when(chatService.getChatPrefix(CHAT_ID)).thenReturn(Optional.of(CHAT_PREFIX));
        when(userService.getUserFullNameInRequiredCase(TARGET_USER_ID, NameCase.INSTRUMENTAL))
                .thenReturn(USER_NAME_INSTRUMENTAL);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(eq(true), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        ApiException apiException = new ApiException(new Error().setErrorCode(1).setErrorMsg("API error"));
        doThrow(apiException).when(vkChatClient).sendText(any(SendMessageDto.class));

        try (MockedStatic<TextUtils> textUtilsMock = mockStatic(TextUtils.class)) {
            textUtilsMock.when(() -> TextUtils.createMention(TARGET_USER_ID)).thenReturn(MENTION);

            assertThrows(ApiException.class, () -> command.execute(commandMessage));
            verify(vkChatClient).sendText(sendMessageDto);
        }
    }

    @Test
    void shouldPropagateRuntimeExceptionFromMemberPermissionService() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"@user", "ban"});
        when(commandMessage.getAllRows()).thenReturn(new String[]{"@user", "ban"});
        when(userInputResolver.getMemberIdByStringInput(CHAT_ID, "@user")).thenReturn(Optional.of(TARGET_USER_ID));

        when(memberPermissionService.allowOrForbidCommandForMember(
                eq(CHAT_ID), eq(FROM_ID), anySet(), eq(TARGET_USER_ID), eq(true)))
                .thenThrow(new RuntimeException("Unexpected DB error"));

        assertThrows(RuntimeException.class, () -> command.execute(commandMessage));
        verify(vkChatClient, never()).sendText(any());
    }

    @Test
    void shouldPropagateRuntimeExceptionFromUserService() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"@user", "ban"});
        when(commandMessage.getAllRows()).thenReturn(new String[]{"@user", "ban"});
        when(userInputResolver.getMemberIdByStringInput(CHAT_ID, "@user")).thenReturn(Optional.of(TARGET_USER_ID));

        MemberPermissionSettingResult result = new MemberPermissionSettingResult();
        result.setAccepted(Set.of("ban"));
        when(memberPermissionService.allowOrForbidCommandForMember(
                eq(CHAT_ID), eq(FROM_ID), anySet(), eq(TARGET_USER_ID), eq(true)))
                .thenReturn(result);
        when(chatService.getChatPrefix(CHAT_ID)).thenReturn(Optional.of(CHAT_PREFIX));

        when(userService.getUserFullNameInRequiredCase(TARGET_USER_ID, NameCase.INSTRUMENTAL))
                .thenThrow(new RuntimeException("User service error"));

        assertThrows(RuntimeException.class, () -> command.execute(commandMessage));
        verify(vkChatClient, never()).sendText(any());
    }
}