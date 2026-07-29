package com.example.my_bot.unit.command.commands.member;

import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.commands.member.UnassignExitedMembersCommand;
import com.example.my_bot.dto.RoleDto;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.command.CommandRoutingData;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.service.MemberService;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import com.vk.api.sdk.objects.base.Error;

@ExtendWith(MockitoExtension.class)
class UnassignExitedMembersCommandTest {

    private static final long CHAT_ID = 100L;
    private static final long FROM_ID = 200L;
    private static final String ROLE_NAME = "Модератор";
    private static final int ROLE_PRIORITY = 5;

    @Mock
    private MemberService memberService;

    @Mock
    private VkChatClient vkChatClient;

    @Mock
    private MessageMapper messageMapper;

    @Mock
    private CommandMessageDto commandMessage;

    @Mock
    private CommandRoutingData commandRoutingData;

    @Mock
    private GroupActor groupActor;

    private UnassignExitedMembersCommand unassignExitedMembersCommand;

    @BeforeEach
    void setUp() {
        unassignExitedMembersCommand = new UnassignExitedMembersCommand(
                memberService,
                messageMapper
        );
        unassignExitedMembersCommand.setVkChatService(vkChatClient);

        when(commandMessage.getCommandRoutingData()).thenReturn(commandRoutingData);
        when(commandRoutingData.getDataBaseChatId()).thenReturn(CHAT_ID);
        when(commandMessage.getFromId()).thenReturn(FROM_ID);
    }

    @Test
    void shouldPropagateRuntimeExceptionFromMemberService() throws ClientException, ApiException {
        // given
        when(memberService.removePositiveRoleFromExitedMembers(CHAT_ID, FROM_ID))
                .thenThrow(new RuntimeException("Service error"));

        // when / then
        assertThrows(RuntimeException.class, () -> unassignExitedMembersCommand.execute(commandMessage));
        verify(vkChatClient, never()).sendText(any());
        verify(messageMapper, never()).toSendMessageDto(anyString(), any(CommandMessageDto.class));
    }

    @Test
    void shouldPropagateClientExceptionFromSendText() throws ClientException, ApiException {
        // given
        RoleDto callerRole = new RoleDto(ROLE_NAME, ROLE_PRIORITY);
        when(memberService.removePositiveRoleFromExitedMembers(CHAT_ID, FROM_ID))
                .thenReturn(callerRole);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(anyString(), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        ClientException clientException = new ClientException("VK client error");
        doThrow(clientException).when(vkChatClient).sendText(any(SendMessageDto.class));

        // when / then
        assertThrows(ClientException.class, () -> unassignExitedMembersCommand.execute(commandMessage));
        verify(vkChatClient).sendText(sendMessageDto);
    }

    @Test
    void shouldPropagateApiExceptionFromSendText() throws ClientException, ApiException {
        // given
        RoleDto callerRole = new RoleDto(ROLE_NAME, ROLE_PRIORITY);
        when(memberService.removePositiveRoleFromExitedMembers(CHAT_ID, FROM_ID))
                .thenReturn(callerRole);

        SendMessageDto sendMessageDto = new SendMessageDto("", 1L, groupActor, null, false, false, null);
        when(messageMapper.toSendMessageDto(anyString(), any(CommandMessageDto.class)))
                .thenReturn(sendMessageDto);

        ApiException apiException = new ApiException(new Error().setErrorCode(1).setErrorMsg("API error"));
        doThrow(apiException).when(vkChatClient).sendText(any(SendMessageDto.class));

        // when / then
        assertThrows(ApiException.class, () -> unassignExitedMembersCommand.execute(commandMessage));
        verify(vkChatClient).sendText(sendMessageDto);
    }
}