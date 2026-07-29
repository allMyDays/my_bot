package com.example.my_bot.unit.command.commands;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.commands.DeleteMessagesCommand;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.command.CommandRoutingData;
import com.example.my_bot.enumeration.command.CommandExecutionStatus;
import com.example.my_bot.exception.member.MemberException;
import com.example.my_bot.exception.message.MessageException;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.resolver.UserInputResolver;
import com.example.my_bot.service.MessageLogService;
import com.example.my_bot.utils.TimeUtils;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.objects.base.Error;
import com.vk.api.sdk.objects.messages.ForeignMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeleteMessagesCommandTest {

    private static final long CHAT_ID = 100L;
    private static final long FROM_ID = 200L;
    private static final long TARGET_USER_ID = 300L;
    private static final long BOT_GROUP_ID = 400L;
    private static final long DEFAULT_PERIOD = 604_800L;
    private static final int LIMIT = 500;

    @Mock
    private MessageMapper messageMapper;

    @Mock
    private VkChatClient vkChatClient;

    @Mock
    private UserInputResolver userInputResolver;

    @Mock
    private MessageLogService messageLogService;

    @Mock
    private CommandMessageDto commandMessage;

    @Mock
    private CommandRoutingData commandRoutingData;

    @Mock
    private GroupActor groupActor;

    @Mock
    private ForeignMessage foreignMessage;

    private DeleteMessagesCommand deleteMessagesCommand;

    @BeforeEach
    void setUp() {
        deleteMessagesCommand = new DeleteMessagesCommand(messageMapper, userInputResolver);
        deleteMessagesCommand.setVkChatClient(vkChatClient);
        deleteMessagesCommand.setMessageLogService(messageLogService);

        when(commandMessage.getCommandRoutingData()).thenReturn(commandRoutingData);
        when(commandRoutingData.getDataBaseChatId()).thenReturn(CHAT_ID);
        when(commandRoutingData.getExecutorBot()).thenReturn(groupActor);
        when(groupActor.getGroupId()).thenReturn(BOT_GROUP_ID);
        when(commandMessage.getFromId()).thenReturn(FROM_ID);
    }

    @Test
    void shouldShowHelpWhenNoArgumentsAndNoForwardedMessages() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});
        when(commandMessage.getReplyOrFwdMessages()).thenReturn(List.of());

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = deleteMessagesCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        String actual = captor.getValue().getText();
        assertTrue(actual.contains("Справка по использованию"));
        assertTrue(actual.contains("1) !удаление и [пересланные сообщения]"));
        verify(messageLogService, never()).findNotDeletedMessageIdsOfNotAChatAdminOwner(anyLong(), anyLong(), anyLong(), anyInt(), anyLong());
        verify(vkChatClient, never()).batchDeleteMessagesInAConversation(any(), any());
    }

    @Test
    void shouldDeleteForwardedMessagesSuccess() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});
        List<Integer> msgIds = List.of(1, 2, 3);
        when(foreignMessage.getConversationMessageId()).thenReturn(1, 2, 3);
        when(commandMessage.getReplyOrFwdMessages()).thenReturn(List.of(foreignMessage, foreignMessage, foreignMessage));

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        Set<Integer> deletedIds = Set.of(1, 2, 3);
        when(vkChatClient.batchDeleteMessagesInAConversation(eq(commandRoutingData), eq(msgIds))).thenReturn(deletedIds);

        CommandExecutionStatus status = deleteMessagesCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);
        verify(vkChatClient).batchDeleteMessagesInAConversation(commandRoutingData, msgIds);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        String actual = captor.getValue().getText();
        assertEquals("✅Было успешно удалено 3 из 3 сообщений.\n", actual);
        verify(messageLogService, never()).findNotDeletedMessageIdsOfNotAChatAdminOwner(anyLong(), anyLong(), anyLong(), anyInt(), anyLong());
    }

    @Test
    void shouldDeleteForwardedMessagesWithPartialSuccess() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});
        List<Integer> msgIds = List.of(1, 2, 3);
        when(foreignMessage.getConversationMessageId()).thenReturn(1, 2, 3);
        when(commandMessage.getReplyOrFwdMessages()).thenReturn(List.of(foreignMessage, foreignMessage, foreignMessage));

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        Set<Integer> deletedIds = Set.of(1, 2);
        when(vkChatClient.batchDeleteMessagesInAConversation(eq(commandRoutingData), eq(msgIds))).thenReturn(deletedIds);

        CommandExecutionStatus status = deleteMessagesCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        String actual = captor.getValue().getText();
        System.out.println(actual);
        assertEquals("✅Было успешно удалено 2 из 3 сообщений.\n" +
                "Остальные 1 сообщений вероятно уже были удалены, либо сообщения принадлежат создателю/администратору чата.", actual);
    }

    @Test
    void shouldDeleteMessagesByUserWithDefaultPeriod() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"@user"});
        when(userInputResolver.getMemberIdByStringInput(CHAT_ID, "@user")).thenReturn(Optional.of(TARGET_USER_ID));

        List<Integer> msgIds = List.of(10, 20, 30);
        Page<Integer> page = new PageImpl<>(msgIds, PageRequest.of(0, LIMIT), 3);
        when(messageLogService.findNotDeletedMessageIdsOfNotAChatAdminOwner(
                eq(CHAT_ID), eq(TARGET_USER_ID), eq(DEFAULT_PERIOD), eq(LIMIT), eq(BOT_GROUP_ID)))
                .thenReturn(page);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        Set<Integer> deletedIds = Set.of(10, 20, 30);
        when(vkChatClient.batchDeleteMessagesInAConversation(eq(commandRoutingData), eq(msgIds))).thenReturn(deletedIds);

        CommandExecutionStatus status = deleteMessagesCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);
        verify(messageLogService).findNotDeletedMessageIdsOfNotAChatAdminOwner(CHAT_ID, TARGET_USER_ID, DEFAULT_PERIOD, LIMIT, BOT_GROUP_ID);
        verify(vkChatClient).batchDeleteMessagesInAConversation(commandRoutingData, msgIds);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertEquals("✅Было успешно удалено 3 из 3 сообщений.\n", captor.getValue().getText());
    }

    @Test
    void shouldDeleteMessagesByUserWithCustomPeriod() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"@user", "2", "hours"});
        when(userInputResolver.getMemberIdByStringInput(CHAT_ID, "@user")).thenReturn(Optional.of(TARGET_USER_ID));

        try (MockedStatic<TimeUtils> timeUtils = mockStatic(TimeUtils.class)) {
            long period = 7200L;
            timeUtils.when(() -> TimeUtils.toSecondsFromString("2", "hours")).thenReturn(Optional.of(period));

            List<Integer> msgIds = List.of(5, 6);
            Page<Integer> page = new PageImpl<>(msgIds, PageRequest.of(0, LIMIT), 2);
            when(messageLogService.findNotDeletedMessageIdsOfNotAChatAdminOwner(
                    eq(CHAT_ID), eq(TARGET_USER_ID), eq(period), eq(LIMIT), eq(BOT_GROUP_ID)))
                    .thenReturn(page);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            Set<Integer> deletedIds = Set.of(5, 6);
            when(vkChatClient.batchDeleteMessagesInAConversation(eq(commandRoutingData), eq(msgIds))).thenReturn(deletedIds);

            CommandExecutionStatus status = deleteMessagesCommand.execute(commandMessage);

            assertEquals(CommandExecutionStatus.SUCCESS, status);
            verify(messageLogService).findNotDeletedMessageIdsOfNotAChatAdminOwner(CHAT_ID, TARGET_USER_ID, period, LIMIT, BOT_GROUP_ID);
        }
    }


    @Test
    void shouldDeleteAllNonAdminMessagesByPeriod() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"3", "days"});

        try (MockedStatic<TimeUtils> timeUtils = mockStatic(TimeUtils.class)) {
            long period = 259200L;
            timeUtils.when(() -> TimeUtils.toSecondsFromString("3", "days")).thenReturn(Optional.of(period));

            List<Integer> msgIds = List.of(100, 101);
            Page<Integer> page = new PageImpl<>(msgIds, PageRequest.of(0, LIMIT), 2);
            when(messageLogService.findNotDeletedMessageIdsOfNotChatAdminOwners(
                    eq(CHAT_ID), eq(period), eq(LIMIT), eq(BOT_GROUP_ID)))
                    .thenReturn(page);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            Set<Integer> deletedIds = Set.of(100, 101);
            when(vkChatClient.batchDeleteMessagesInAConversation(eq(commandRoutingData), eq(msgIds))).thenReturn(deletedIds);

            CommandExecutionStatus status = deleteMessagesCommand.execute(commandMessage);

            assertEquals(CommandExecutionStatus.SUCCESS, status);
            verify(messageLogService).findNotDeletedMessageIdsOfNotChatAdminOwners(CHAT_ID, period, LIMIT, BOT_GROUP_ID);
        }
    }

    @Test
    void shouldReturnArgumentValidationErrorWhenInvalidMemberLink() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"неверный"});
        when(userInputResolver.getMemberIdByStringInput(CHAT_ID, "неверный")).thenReturn(Optional.empty());

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = deleteMessagesCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.ARGUMENT_VALIDATION_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        System.out.println(captor.getValue().getText());
        assertTrue(captor.getValue().getText().contains("Не удалось получить участника по указанному вами строчному аргументу."));
        verify(messageLogService, never()).findNotDeletedMessageIdsOfNotAChatAdminOwner(anyLong(), anyLong(), anyLong(), anyInt(), anyLong());
    }

    @Test
    void shouldReturnArgumentValidationErrorWhenInvalidTimePeriod() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"@user", "invalid", "time"});
        when(userInputResolver.getMemberIdByStringInput(CHAT_ID, "@user")).thenReturn(Optional.of(TARGET_USER_ID));

        try (MockedStatic<TimeUtils> timeUtils = mockStatic(TimeUtils.class)) {
            timeUtils.when(() -> TimeUtils.toSecondsFromString("invalid", "time")).thenReturn(Optional.empty());

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            CommandExecutionStatus status = deleteMessagesCommand.execute(commandMessage);

            assertEquals(CommandExecutionStatus.ARGUMENT_VALIDATION_ERROR, status);
            verify(vkChatClient).sendText(sendMessageDto);
            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            System.out.println(captor.getValue().getText());
            assertTrue(captor.getValue().getText().contains("Указанный вами аргумент не является корректным временным периодом. Правильный пример: 2 минуты / 3 часа / 1 день"));
            verify(messageLogService, never()).findNotDeletedMessageIdsOfNotAChatAdminOwner(anyLong(), anyLong(), anyLong(), anyInt(), anyLong());
        }
    }

    @Test
    void shouldReturnArgumentValidationErrorWhenInvalidPeriodForAllNonAdmin() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"invalid", "period"});

        try (MockedStatic<TimeUtils> timeUtils = mockStatic(TimeUtils.class)) {
            timeUtils.when(() -> TimeUtils.toSecondsFromString("invalid", "period")).thenReturn(Optional.empty());

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            CommandExecutionStatus status = deleteMessagesCommand.execute(commandMessage);

            assertEquals(CommandExecutionStatus.ARGUMENT_VALIDATION_ERROR, status);
            verify(vkChatClient).sendText(sendMessageDto);
            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            System.out.println(captor.getValue().getText());
            assertTrue(captor.getValue().getText().contains("Указанный вами аргумент не является корректным временным периодом. Правильный пример: 2 минуты / 3 часа / 1 день"));
            verify(messageLogService, never()).findNotDeletedMessageIdsOfNotChatAdminOwners(anyLong(), anyLong(), anyInt(), anyLong());
        }
    }

    @Test
    void shouldReturnBusinessLogicErrorOnMemberExceptionFromFindByUser() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"@user"});
        when(userInputResolver.getMemberIdByStringInput(CHAT_ID, "@user")).thenReturn(Optional.of(TARGET_USER_ID));

        MemberException exception = new MemberException("Member error") {};
        when(messageLogService.findNotDeletedMessageIdsOfNotAChatAdminOwner(
                eq(CHAT_ID), eq(TARGET_USER_ID), eq(DEFAULT_PERIOD), eq(LIMIT), eq(BOT_GROUP_ID)))
                .thenThrow(exception);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = deleteMessagesCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertEquals(exception.getMessage(), captor.getValue().getText());
        verify(vkChatClient, never()).batchDeleteMessagesInAConversation(any(), any());
    }

    @Test
    void shouldReturnBusinessLogicErrorOnMessageExceptionFromFindByUser() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"@user"});
        when(userInputResolver.getMemberIdByStringInput(CHAT_ID, "@user")).thenReturn(Optional.of(TARGET_USER_ID));

        MessageException exception = new MessageException("Message error") {};
        when(messageLogService.findNotDeletedMessageIdsOfNotAChatAdminOwner(
                eq(CHAT_ID), eq(TARGET_USER_ID), eq(DEFAULT_PERIOD), eq(LIMIT), eq(BOT_GROUP_ID)))
                .thenThrow(exception);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = deleteMessagesCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertEquals(exception.getMessage(), captor.getValue().getText());
        verify(vkChatClient, never()).batchDeleteMessagesInAConversation(any(), any());
    }

    @Test
    void shouldReturnBusinessLogicErrorOnMemberExceptionFromFindAllNonAdmin() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"3", "days"});

        try (MockedStatic<TimeUtils> timeUtils = mockStatic(TimeUtils.class)) {
            timeUtils.when(() -> TimeUtils.toSecondsFromString("3", "days")).thenReturn(Optional.of(259200L));

            MemberException exception = new MemberException("Member error") {};
            when(messageLogService.findNotDeletedMessageIdsOfNotChatAdminOwners(
                    eq(CHAT_ID), eq(259200L), eq(LIMIT), eq(BOT_GROUP_ID)))
                    .thenThrow(exception);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            CommandExecutionStatus status = deleteMessagesCommand.execute(commandMessage);

            assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
            verify(vkChatClient).sendText(sendMessageDto);
            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            assertEquals(exception.getMessage(), captor.getValue().getText());
            verify(vkChatClient, never()).batchDeleteMessagesInAConversation(any(), any());
        }
    }

    @Test
    void shouldReturnBusinessLogicErrorOnMessageExceptionFromFindAllNonAdmin() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"3", "days"});

        try (MockedStatic<TimeUtils> timeUtils = mockStatic(TimeUtils.class)) {
            timeUtils.when(() -> TimeUtils.toSecondsFromString("3", "days")).thenReturn(Optional.of(259200L));

            MessageException exception = new MessageException("Message error") {};
            when(messageLogService.findNotDeletedMessageIdsOfNotChatAdminOwners(
                    eq(CHAT_ID), eq(259200L), eq(LIMIT), eq(BOT_GROUP_ID)))
                    .thenThrow(exception);

            SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
            when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

            CommandExecutionStatus status = deleteMessagesCommand.execute(commandMessage);

            assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
            verify(vkChatClient).sendText(sendMessageDto);
            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient).sendText(captor.capture());
            assertEquals(exception.getMessage(), captor.getValue().getText());
            verify(vkChatClient, never()).batchDeleteMessagesInAConversation(any(), any());
        }
    }

    @Test
    void shouldSendProgressMessageWhenMoreThanThresholdMessages() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"@user"});
        when(userInputResolver.getMemberIdByStringInput(CHAT_ID, "@user")).thenReturn(Optional.of(TARGET_USER_ID));

        List<Integer> msgIds = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11);
        List<Integer> manyMsgIds = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20,
                21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40,
                41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60,
                61, 62, 63, 64, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80,
                81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 91, 92, 93, 94, 95, 96, 97, 98, 99, 100, 101);
        Page<Integer> page = new PageImpl<>(manyMsgIds, PageRequest.of(0, LIMIT), 101);
        when(messageLogService.findNotDeletedMessageIdsOfNotAChatAdminOwner(
                eq(CHAT_ID), eq(TARGET_USER_ID), eq(DEFAULT_PERIOD), eq(LIMIT), eq(BOT_GROUP_ID)))
                .thenReturn(page);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        Set<Integer> deletedIds = Set.of(1, 2, 3);
        when(vkChatClient.batchDeleteMessagesInAConversation(eq(commandRoutingData), eq(manyMsgIds))).thenReturn(deletedIds);

        CommandExecutionStatus status = deleteMessagesCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);
        verify(vkChatClient, times(2)).sendText(any(SendMessageDto.class));
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient, times(2)).sendText(captor.capture());
        List<SendMessageDto> allMessages = captor.getAllValues();

        assertEquals("Отправляю запрос на удаление 101 из 101 сообщений..", allMessages.get(0).getText());
        assertEquals("✅Было успешно удалено 3 из 101 сообщений.\n" +
                "Остальные 98 сообщений вероятно уже были удалены.", allMessages.get(1).getText());
    }

    @Test
    void shouldPropagateClientExceptionFromBatchDelete() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"@user"});
        when(userInputResolver.getMemberIdByStringInput(CHAT_ID, "@user")).thenReturn(Optional.of(TARGET_USER_ID));

        List<Integer> msgIds = List.of(1, 2);
        Page<Integer> page = new PageImpl<>(msgIds, PageRequest.of(0, LIMIT), 2);
        when(messageLogService.findNotDeletedMessageIdsOfNotAChatAdminOwner(
                eq(CHAT_ID), eq(TARGET_USER_ID), eq(DEFAULT_PERIOD), eq(LIMIT), eq(BOT_GROUP_ID)))
                .thenReturn(page);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        ClientException clientException = new ClientException("VK client error");
        when(vkChatClient.batchDeleteMessagesInAConversation(eq(commandRoutingData), eq(msgIds)))
                .thenThrow(clientException);

        assertThrows(ClientException.class, () -> deleteMessagesCommand.execute(commandMessage));
        verify(vkChatClient, never()).sendText(any()); // не отправляет финальное сообщение
    }

    @Test
    void shouldPropagateApiExceptionFromBatchDelete() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"@user"});
        when(userInputResolver.getMemberIdByStringInput(CHAT_ID, "@user")).thenReturn(Optional.of(TARGET_USER_ID));

        List<Integer> msgIds = List.of(1, 2);
        Page<Integer> page = new PageImpl<>(msgIds, PageRequest.of(0, LIMIT), 2);
        when(messageLogService.findNotDeletedMessageIdsOfNotAChatAdminOwner(
                eq(CHAT_ID), eq(TARGET_USER_ID), eq(DEFAULT_PERIOD), eq(LIMIT), eq(BOT_GROUP_ID)))
                .thenReturn(page);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        ApiException apiException = new ApiException(new Error().setErrorCode(1).setErrorMsg("API error"));
        when(vkChatClient.batchDeleteMessagesInAConversation(eq(commandRoutingData), eq(msgIds)))
                .thenThrow(apiException);

        assertThrows(ApiException.class, () -> deleteMessagesCommand.execute(commandMessage));
        verify(vkChatClient, never()).sendText(any());
    }

    @Test
    void shouldPropagateClientExceptionFromSendText() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{}); // справка
        when(commandMessage.getReplyOrFwdMessages()).thenReturn(List.of());

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        ClientException clientException = new ClientException("VK send error");
        doThrow(clientException).when(vkChatClient).sendText(any(SendMessageDto.class));

        assertThrows(ClientException.class, () -> deleteMessagesCommand.execute(commandMessage));
        verify(vkChatClient).sendText(sendMessageDto);
    }

    @Test
    void shouldPropagateApiExceptionFromSendText() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{}); // справка
        when(commandMessage.getReplyOrFwdMessages()).thenReturn(List.of());

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        ApiException apiException = new ApiException(new Error().setErrorCode(1).setErrorMsg("API error"));
        doThrow(apiException).when(vkChatClient).sendText(any(SendMessageDto.class));

        assertThrows(ApiException.class, () -> deleteMessagesCommand.execute(commandMessage));
        verify(vkChatClient).sendText(sendMessageDto);
    }
}