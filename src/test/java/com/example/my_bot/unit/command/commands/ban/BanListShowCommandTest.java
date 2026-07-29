package com.example.my_bot.unit.command.commands.ban;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.commands.ban.BanListShowCommand;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.command.CommandRoutingData;
import com.example.my_bot.entity.BanEntity;
import com.example.my_bot.enumeration.TimeZoneType;
import com.example.my_bot.enumeration.command.CommandExecutionStatus;
import com.example.my_bot.enumeration.user.NameCase;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.service.BanService;
import com.example.my_bot.service.GlobalUserService;
import com.example.my_bot.service.chat.ChatService;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.objects.base.Error;
import com.vk.api.sdk.objects.base.ErrorInnerType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BanListShowCommandTest {

    private static final long CHAT_ID = 100L;
    private static final long USER_ID_1 = 200L;
    private static final long USER_ID_2 = 201L;
    private static final String USER_NAME_1 = "Иван Иванов";
    private static final String USER_NAME_2 = "Петр Петров";
    private static final TimeZoneType TIME_ZONE = TimeZoneType.GMT_PLUS_3;

    @Mock
    private VkChatClient vkChatClient;

    @Mock
    private MessageMapper messageMapper;

    @Mock
    private GlobalUserService globalUserService;

    @Mock
    private BanService banService;

    @Mock
    private ChatService chatService;

    @Mock
    private CommandMessageDto commandMessage;

    @Mock
    private CommandRoutingData commandRoutingData;

    @Mock
    private GroupActor groupActor;

    @InjectMocks
    private BanListShowCommand banListShowCommand;

    @BeforeEach
    void setUp() {
        banListShowCommand = new BanListShowCommand(vkChatClient, messageMapper, globalUserService, banService, chatService);
    }

    @Test
    void shouldShowTemporaryBansSuccess() throws ClientException, ApiException {
        mockCommonCommandData();
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});

        Instant bannedUntil = Instant.now().plusSeconds(3600);
        BanEntity ban1 = createBan(USER_ID_1, bannedUntil);
        BanEntity ban2 = createBan(USER_ID_2, bannedUntil);
        List<BanEntity> bans = List.of(ban1, ban2);
        Page<BanEntity> page = new PageImpl<>(bans, PageRequest.of(0, 500), 2);

        when(banService.getAllChatTemporaryBans(CHAT_ID, 500)).thenReturn(page);
        when(chatService.getChatTimeZone(CHAT_ID)).thenReturn(TIME_ZONE);

        Map<Long, String> namesMap = Map.of(USER_ID_1, USER_NAME_1, USER_ID_2, USER_NAME_2);
        when(globalUserService.getUserFullNamesInRequiredCase(anySet(), eq(NameCase.NOMINATIVE))).thenReturn(namesMap);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(anyString(), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = banListShowCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);
        verify(banService).getAllChatTemporaryBans(CHAT_ID, 500);
        verify(globalUserService).getUserFullNamesInRequiredCase(anySet(), eq(NameCase.NOMINATIVE));
        verify(vkChatClient).sendText(sendMessageDto);

        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(messageMapper).toSendMessageDto(textCaptor.capture(), any(CommandMessageDto.class));
        String actual = textCaptor.getValue();

        assertTrue(actual.contains("во временном бане:"));
        assertTrue(actual.contains(USER_NAME_1));
        assertTrue(actual.contains(USER_NAME_2));
        assertTrue(actual.contains("до "));
        assertTrue(actual.contains("Даты окончания блокировок указаны по " + TIME_ZONE.getStringType()));
        assertTrue(actual.contains("Чтобы посмотреть пользователей в вечном бане, добавьте аргумент «вечные»."));
    }

    @Test
    void shouldShowPermanentBansSuccess() throws ClientException, ApiException {
        mockCommonCommandData();
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"вечные"});

        BanEntity ban1 = createBan(USER_ID_1, null);
        BanEntity ban2 = createBan(USER_ID_2, null);
        List<BanEntity> bans = List.of(ban1, ban2);
        Page<BanEntity> page = new PageImpl<>(bans, PageRequest.of(0, 500), 2);

        when(banService.getAllChatPermanentBans(CHAT_ID, 500)).thenReturn(page);
        when(chatService.getChatTimeZone(CHAT_ID)).thenReturn(TIME_ZONE);

        Map<Long, String> namesMap = Map.of(USER_ID_1, USER_NAME_1, USER_ID_2, USER_NAME_2);
        when(globalUserService.getUserFullNamesInRequiredCase(anySet(), eq(NameCase.NOMINATIVE))).thenReturn(namesMap);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(anyString(), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = banListShowCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);
        verify(banService).getAllChatPermanentBans(CHAT_ID, 500);
        verify(vkChatClient).sendText(sendMessageDto);

        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(messageMapper).toSendMessageDto(textCaptor.capture(), any(CommandMessageDto.class));
        String actual = textCaptor.getValue();

        assertTrue(actual.contains("в вечном бане:"));
        assertTrue(actual.contains(USER_NAME_1));
        assertTrue(actual.contains(USER_NAME_2));
        assertTrue(actual.contains("∞"));
        assertFalse(actual.contains("Даты окончания блокировок"));
        assertFalse(actual.contains("Чтобы посмотреть пользователей в вечном бане"));
    }

    @Test
    void shouldHandleEmptyList() throws ClientException, ApiException {
        mockCommonCommandData();
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});

        Page<BanEntity> page = new PageImpl<>(List.of(), PageRequest.of(0, 500), 0);
        when(banService.getAllChatTemporaryBans(CHAT_ID, 500)).thenReturn(page);
        when(chatService.getChatTimeZone(CHAT_ID)).thenReturn(TIME_ZONE);

        when(globalUserService.getUserFullNamesInRequiredCase(anySet(), eq(NameCase.NOMINATIVE))).thenReturn(Map.of());

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(anyString(), any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = banListShowCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);

        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(messageMapper).toSendMessageDto(textCaptor.capture(), any(CommandMessageDto.class));
        String actual = textCaptor.getValue();

        assertTrue(actual.contains("во временном бане:"));
        assertFalse(actual.contains("Было показано"));
        assertTrue(actual.contains("Чтобы посмотреть пользователей в вечном бане, добавьте аргумент «вечные»."));
    }

    @Test
    void shouldPropagateRuntimeExceptionFromBanService() throws ClientException, ApiException {
        mockCommonCommandData();
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});

        RuntimeException runtimeException = new RuntimeException("Service error");
        when(banService.getAllChatTemporaryBans(CHAT_ID, 500)).thenThrow(runtimeException);

        assertThrows(RuntimeException.class, () -> banListShowCommand.execute(commandMessage));

        verify(banService).getAllChatTemporaryBans(CHAT_ID, 500);
        verify(vkChatClient, never()).sendText(any());
        verify(messageMapper, never()).toSendMessageDto(anyString(), any(CommandMessageDto.class));
    }


    private void mockCommonCommandData() {
        when(commandMessage.getCommandRoutingData()).thenReturn(commandRoutingData);
        when(commandRoutingData.getDataBaseChatId()).thenReturn(CHAT_ID);
    }

    private BanEntity createBan(long memberId, Instant bannedUntil) {
        BanEntity ban = new BanEntity();
        ban.setMemberId(memberId);
        ban.setBannedUntil(bannedUntil);
        ban.setChatId(CHAT_ID);
        ban.setBannedAt(Instant.now());
        ban.setBannedBy(1L);
        ban.setReason(null);
        return ban;
    }
}