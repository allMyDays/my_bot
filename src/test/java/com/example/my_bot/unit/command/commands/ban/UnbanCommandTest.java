package com.example.my_bot.unit.command.commands.ban;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.commands.ban.UnbanCommand;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.command.CommandRoutingData;
import com.example.my_bot.dto.member.ParseMemberInputResult;
import com.example.my_bot.enumeration.command.CommandExecutionStatus;
import com.example.my_bot.enumeration.user.NameCase;
import com.example.my_bot.exception.ban.BanException;
import com.example.my_bot.exception.ban.UserHasNotBannedException;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.resolver.UserInputResolver;
import com.example.my_bot.service.BanService;
import com.example.my_bot.service.GlobalUserService;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import com.vk.api.sdk.objects.base.Error;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UnbanCommandTest {

    private static final long CHAT_ID = 100L;
    private static final long USER_ID = 200L;
    private static final String USER_NAME_GENITIVE = "Ивана Иванова";

    @Mock
    private VkChatClient vkChatClient;

    @Mock
    private BanService banService;

    @Mock
    private UserInputResolver userInputResolver;

    @Mock
    private GlobalUserService globalUserService;

    @Mock
    private MessageMapper messageMapper;

    @Mock
    private CommandMessageDto commandMessage;

    @Mock
    private CommandRoutingData commandRoutingData;

    @Mock
    private GroupActor groupActor;

    @InjectMocks
    private UnbanCommand unbanCommand;

    @BeforeEach
    void setUp() {
        unbanCommand = new UnbanCommand(banService, userInputResolver, globalUserService, messageMapper);
        unbanCommand.setVkChatClient(vkChatClient);

        when(commandMessage.getCommandRoutingData()).thenReturn(commandRoutingData);
        when(commandRoutingData.getDataBaseChatId()).thenReturn(CHAT_ID);
    }


    @Test
    void shouldUnbanUserSuccess() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"@durov"});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(USER_ID, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        doNothing().when(banService).deleteMemberBan(CHAT_ID, USER_ID);

        when(globalUserService.getUserFullNameInRequiredCase(USER_ID, NameCase.GENITIVE))
                .thenReturn(USER_NAME_GENITIVE);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = unbanCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.SUCCESS, status);
        verify(banService).deleteMemberBan(CHAT_ID, USER_ID);
        verify(vkChatClient).sendText(sendMessageDto);

        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        String actual = captor.getValue().getText();

        assert actual.contains("✅");
        assert actual.contains("@id" + USER_ID);
        assert actual.contains(USER_NAME_GENITIVE);
        assert actual.contains("был успешно снят бан");
        assert actual.contains("самостоятельно пригласить");
    }

    @Test
    void shouldReturnArgumentValidationErrorWhenNoMemberArgument() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(null, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = unbanCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.ARGUMENT_VALIDATION_ERROR, status);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        String actual = captor.getValue().getText();

        assertTrue(actual.contains("участник") || actual.contains("пользователь") || actual.contains("аргумент") || actual.contains("укажите"),
                "Сообщение должно содержать указание на отсутствие участника, но было: " + actual);
        verify(banService, never()).deleteMemberBan(anyLong(), anyLong());
    }

    @Test
    void shouldReturnBusinessLogicErrorOnBanException() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"@durov"});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(USER_ID, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        BanException banException = new UserHasNotBannedException(USER_ID);
        doThrow(banException).when(banService).deleteMemberBan(CHAT_ID, USER_ID);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        CommandExecutionStatus status = unbanCommand.execute(commandMessage);

        assertEquals(CommandExecutionStatus.BUSINESS_LOGIC_ERROR, status);
        verify(banService).deleteMemberBan(CHAT_ID, USER_ID);
        verify(vkChatClient).sendText(sendMessageDto);
        ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
        verify(vkChatClient).sendText(captor.capture());
        assertEquals(banException.getMessage(), captor.getValue().getText());
        verify(globalUserService, never()).getUserFullNameInRequiredCase(anyLong(), any());
    }

    @Test
    void shouldPropagateClientExceptionFromVkClient() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"@durov"});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(USER_ID, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        doNothing().when(banService).deleteMemberBan(CHAT_ID, USER_ID);

        when(globalUserService.getUserFullNameInRequiredCase(USER_ID, NameCase.GENITIVE))
                .thenReturn(USER_NAME_GENITIVE);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(any(CommandMessageDto.class))).thenReturn(sendMessageDto);

        ClientException clientException = new ClientException("VK client error");
        doThrow(clientException).when(vkChatClient).sendText(sendMessageDto);

        assertThrows(ClientException.class, () -> unbanCommand.execute(commandMessage));
        verify(banService).deleteMemberBan(CHAT_ID, USER_ID);
        verify(vkChatClient).sendText(sendMessageDto);
    }

    @Test
    void shouldPropagateRuntimeExceptionFromBanService() throws ClientException, ApiException {
        when(commandMessage.getFirstRowArguments()).thenReturn(new String[]{"@durov"});

        ParseMemberInputResult parseResult = new ParseMemberInputResult(USER_ID, false);
        when(userInputResolver.getMemberIdByAnyInput(commandMessage, 0)).thenReturn(parseResult);

        RuntimeException runtimeException = new RuntimeException("Unexpected error");
        doThrow(runtimeException).when(banService).deleteMemberBan(CHAT_ID, USER_ID);

        assertThrows(RuntimeException.class, () -> unbanCommand.execute(commandMessage));
        verify(banService).deleteMemberBan(CHAT_ID, USER_ID);
        verify(vkChatClient, never()).sendText(any());
    }
}
