package com.example.my_bot.unit.service.submanager;

import com.example.my_bot.cache.value.callback.GroupIdAndChatId;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.config.CaffeineCacheManager;
import com.example.my_bot.dto.SendMessageDto;
import com.example.my_bot.dto.submanager.SubmanagerDto;
import com.example.my_bot.entity.ChatEntity;
import com.example.my_bot.exception.submanager.CannotFindSubmanagerChatIdByMainChatIdException;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.service.chat.ChatService;
import com.example.my_bot.service.submanager.SubmanagerActionService;
import com.example.my_bot.utils.ChatUtils;
import com.example.my_bot.utils.SubmanagerUtils;
import com.github.benmanes.caffeine.cache.Cache;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubmanagerActionServiceTest {

    @Mock
    private CaffeineCacheManager cacheManager;

    @Mock
    private ChatService chatService;

    @Mock
    private VkChatClient vkChatClient;

    @Mock
    private MessageMapper messageMapper;

    @Mock
    private Cache<String, GroupIdAndChatId> bindingCache;

    @Mock
    private GroupActor theMainBotGroupActor;

    private SubmanagerActionService submanagerActionService;

    private final long theMainBotId = 777L;
    private final long executorBotId = 999L;
    private final long submanagerChatId = 888L;
    private final long dataBaseChatId = 1L;
    private final long fromId = -theMainBotId;
    private final String bindingCode = "VALID_CODE";
    private final GroupIdAndChatId bindingData = new GroupIdAndChatId(executorBotId, dataBaseChatId);
    private final SubmanagerDto subInfo = new SubmanagerDto(executorBotId, "token", 1, "secret");

    @BeforeEach
    void setUp() {
        lenient().when(cacheManager.getBindingSubmanagerDataCache()).thenReturn(bindingCache);

        submanagerActionService = new SubmanagerActionService(
                cacheManager,
                chatService,
                vkChatClient,
                messageMapper,
                theMainBotId,
                theMainBotGroupActor
        );
    }

    @Test
    void shouldReturnFalseWhenFromIdIsNotMainBot() {
        boolean result = submanagerActionService.tryHandleSubmanagerBinding(
                executorBotId, 123L, submanagerChatId, subInfo, bindingCode
        );
        assertThat(result).isFalse();
        verifyNoInteractions(chatService, vkChatClient);
    }

    @Test
    void shouldReturnFalseWhenBindingCodeIsNull() {
        boolean result = submanagerActionService.tryHandleSubmanagerBinding(
                executorBotId, fromId, submanagerChatId, subInfo, null
        );
        assertThat(result).isFalse();
        verifyNoInteractions(chatService, vkChatClient);
    }

    @Test
    void shouldReturnFalseWhenBindingCodeDoesNotMatchPattern() {
        try (MockedStatic<SubmanagerUtils> utils = mockStatic(SubmanagerUtils.class)) {
            utils.when(() -> SubmanagerUtils.stringMatchesABindingCode("invalid"))
                    .thenReturn(false);

            boolean result = submanagerActionService.tryHandleSubmanagerBinding(
                    executorBotId, fromId, submanagerChatId, subInfo, "invalid"
            );
            assertThat(result).isFalse();
            verifyNoInteractions(chatService, vkChatClient);
        }
    }

    @Test
    void shouldReturnFalseWhenBindingDataNotFoundInCache() {
        try (MockedStatic<SubmanagerUtils> utils = mockStatic(SubmanagerUtils.class)) {
            utils.when(() -> SubmanagerUtils.stringMatchesABindingCode(bindingCode))
                    .thenReturn(true);

            given(bindingCache.getIfPresent(bindingCode)).willReturn(null);

            boolean result = submanagerActionService.tryHandleSubmanagerBinding(
                    executorBotId, fromId, submanagerChatId, subInfo, bindingCode
            );
            assertThat(result).isFalse();
            verifyNoInteractions(chatService, vkChatClient);
        }
    }

    @Test
    void shouldReturnFalseWhenBindingDataGroupIdDoesNotMatch() {
        try (MockedStatic<SubmanagerUtils> utils = mockStatic(SubmanagerUtils.class)) {
            utils.when(() -> SubmanagerUtils.stringMatchesABindingCode(bindingCode))
                    .thenReturn(true);

            GroupIdAndChatId wrongData = new GroupIdAndChatId(888L, dataBaseChatId);
            given(bindingCache.getIfPresent(bindingCode)).willReturn(wrongData);

            boolean result = submanagerActionService.tryHandleSubmanagerBinding(
                    executorBotId, fromId, submanagerChatId, subInfo, bindingCode
            );
            assertThat(result).isFalse();
            verifyNoInteractions(chatService, vkChatClient);
        }
    }

    @Test
    void shouldBindSuccessfullyAndPerformVkActions() throws ApiException, ClientException {
        try (MockedStatic<SubmanagerUtils> utils = mockStatic(SubmanagerUtils.class)) {
            utils.when(() -> SubmanagerUtils.stringMatchesABindingCode(bindingCode))
                    .thenReturn(true);

            given(bindingCache.getIfPresent(bindingCode)).willReturn(bindingData);

            SendMessageDto sendMessage = mock(SendMessageDto.class);
            given(messageMapper.toSendMessageDto(anyString(), anyLong(), anyLong(), any(GroupActor.class)))
                    .willReturn(sendMessage);
            when(vkChatClient.sendText(sendMessage)).thenReturn(Collections.emptyList());
            doNothing().when(vkChatClient).selfLeave(dataBaseChatId, dataBaseChatId, theMainBotGroupActor);
            doNothing().when(vkChatClient).kickOneChatMember(dataBaseChatId, submanagerChatId, subInfo.getGroupActor(), -theMainBotId);

            boolean result = submanagerActionService.tryHandleSubmanagerBinding(
                    executorBotId, fromId, submanagerChatId, subInfo, bindingCode
            );

            assertThat(result).isTrue();
            verify(chatService).setBoundSubmanager(dataBaseChatId, executorBotId, submanagerChatId);
            verify(vkChatClient).sendText(sendMessage);
            verify(vkChatClient).selfLeave(dataBaseChatId, dataBaseChatId, theMainBotGroupActor);
            verify(vkChatClient).kickOneChatMember(dataBaseChatId, submanagerChatId, subInfo.getGroupActor(), -theMainBotId);
        }
    }

    @Test
    void shouldLogErrorIfVkActionsFail() throws ApiException, ClientException {
        try (MockedStatic<SubmanagerUtils> utils = mockStatic(SubmanagerUtils.class)) {
            utils.when(() -> SubmanagerUtils.stringMatchesABindingCode(bindingCode))
                    .thenReturn(true);

            given(bindingCache.getIfPresent(bindingCode)).willReturn(bindingData);

            SendMessageDto sendMessage = mock(SendMessageDto.class);
            given(messageMapper.toSendMessageDto(anyString(), anyLong(), anyLong(), any(GroupActor.class)))
                    .willReturn(sendMessage);
            doThrow(new ClientException("VK error")).when(vkChatClient).sendText(sendMessage);

            boolean result = submanagerActionService.tryHandleSubmanagerBinding(
                    executorBotId, fromId, submanagerChatId, subInfo, bindingCode
            );

            assertThat(result).isTrue();
            verify(chatService).setBoundSubmanager(dataBaseChatId, executorBotId, submanagerChatId);
            verify(vkChatClient).sendText(sendMessage);
            verify(vkChatClient, never()).selfLeave(anyLong(), anyLong(), any());
            verify(vkChatClient, never()).kickOneChatMember(anyLong(), anyLong(), any(), anyLong());
        }
    }

    @Test
    void shouldUnbindAndPerformVkActions() throws ApiException, ClientException {
        GroupActor subToUnbind = new GroupActor(executorBotId, "token");
        SendMessageDto sendMessage = mock(SendMessageDto.class);
        given(messageMapper.toSendMessageDto(anyString(), anyLong(), anyLong(), any(GroupActor.class)))
                .willReturn(sendMessage);
        when(vkChatClient.sendText(sendMessage)).thenReturn(Collections.emptyList());
        doNothing().when(vkChatClient).selfLeave(dataBaseChatId, submanagerChatId, subToUnbind);
        doNothing().when(vkChatClient).kickOneChatMember(dataBaseChatId, dataBaseChatId, theMainBotGroupActor, -executorBotId);

        submanagerActionService.handleSubmanagerUnBinding(dataBaseChatId, subToUnbind, submanagerChatId);

        verify(chatService).setBoundSubmanagerAsNull(dataBaseChatId, executorBotId, submanagerChatId);
        verify(vkChatClient).sendText(sendMessage);
        verify(vkChatClient).selfLeave(dataBaseChatId, submanagerChatId, subToUnbind);
        verify(vkChatClient).kickOneChatMember(dataBaseChatId, dataBaseChatId, theMainBotGroupActor, -executorBotId);
    }

    @Test
    void shouldLogErrorIfUnbindVkActionsFail() throws ApiException, ClientException {
        GroupActor subToUnbind = new GroupActor(executorBotId, "token");
        SendMessageDto sendMessage = mock(SendMessageDto.class);
        given(messageMapper.toSendMessageDto(anyString(), anyLong(), anyLong(), any(GroupActor.class)))
                .willReturn(sendMessage);
        doThrow(new ClientException("VK error")).when(vkChatClient).sendText(sendMessage);

        submanagerActionService.handleSubmanagerUnBinding(dataBaseChatId, subToUnbind, submanagerChatId);

        verify(chatService).setBoundSubmanagerAsNull(dataBaseChatId, executorBotId, submanagerChatId);
        verify(vkChatClient).sendText(sendMessage);
        verify(vkChatClient, never()).selfLeave(anyLong(), anyLong(), any());
        verify(vkChatClient, never()).kickOneChatMember(anyLong(), anyLong(), any(), anyLong());
    }

    @Test
    void shouldNotSendWhenFromIdDoesNotMatchSubmanager() {
        submanagerActionService.sendNewSubPostToRequiredChats(subInfo, 123, 456L);
        verifyNoInteractions(chatService, vkChatClient);
    }

    @Test
    void shouldSendPostToRequiredChatsSuccessfully() throws ApiException, ClientException {
        int postId = 123;
        long fromId = -subInfo.getGroupId();

        ChatEntity chat1 = new ChatEntity();
        chat1.setChatId(10L);
        ChatEntity chat2 = new ChatEntity();
        chat2.setChatId(20L);
        List<ChatEntity> chats = List.of(chat1, chat2);

        given(chatService.findChatsByBoundSubmanagerIdAndSubPostsEnabled(subInfo.getGroupId()))
                .willReturn(chats);

        given(chatService.getSubmanagerChatIdByMainChatId(subInfo.getGroupId(), 10L))
                .willReturn(100L);
        given(chatService.getSubmanagerChatIdByMainChatId(subInfo.getGroupId(), 20L))
                .willReturn(200L);
        SendMessageDto message1 = new SendMessageDto(
                "text1", 0L, theMainBotGroupActor, null, false, false, null
        );
        SendMessageDto message2 = new SendMessageDto(
                "text2", 0L, theMainBotGroupActor, null, false, false, null
        );
        given(messageMapper.toSendMessageDto(anyString(), anyLong(), anyLong(), any(GroupActor.class)))
                .willReturn(message1, message2);

        when(vkChatClient.sendText(message1)).thenReturn(Collections.emptyList());
        when(vkChatClient.sendText(message2)).thenReturn(Collections.emptyList());

        try (MockedStatic<ChatUtils> chatUtilsMock = mockStatic(ChatUtils.class)) {
            chatUtilsMock.when(() -> ChatUtils.buildGroupWallPostAsAttachment(subInfo.getGroupId(), postId))
                    .thenReturn("wall" + subInfo.getGroupId() + "_" + postId);

            submanagerActionService.sendNewSubPostToRequiredChats(subInfo, postId, fromId);

            verify(messageMapper, times(2)).toSendMessageDto(anyString(), anyLong(), anyLong(), any(GroupActor.class));
            verify(vkChatClient, times(2)).sendText(any(SendMessageDto.class));
            ArgumentCaptor<SendMessageDto> captor = ArgumentCaptor.forClass(SendMessageDto.class);
            verify(vkChatClient, times(2)).sendText(captor.capture());
            List<SendMessageDto> sentMessages = captor.getAllValues();
            for (SendMessageDto msg : sentMessages) {
                assertThat(msg.getAttachment()).isEqualTo("wall" + subInfo.getGroupId() + "_" + postId);
            }
        }
    }

    @Test
    void shouldSkipChatIfSubmanagerChatIdNotFound() throws ApiException, ClientException {
        long postId = 123L;
        long fromId = -subInfo.getGroupId();

        ChatEntity chat = new ChatEntity();
        chat.setChatId(10L);
        given(chatService.findChatsByBoundSubmanagerIdAndSubPostsEnabled(subInfo.getGroupId()))
                .willReturn(List.of(chat));

        given(chatService.getSubmanagerChatIdByMainChatId(subInfo.getGroupId(), 10L))
                .willThrow(new CannotFindSubmanagerChatIdByMainChatIdException(subInfo.getGroupId(), 10L));

        submanagerActionService.sendNewSubPostToRequiredChats(subInfo, (int) postId, fromId);

        verify(chatService).findChatsByBoundSubmanagerIdAndSubPostsEnabled(subInfo.getGroupId());
        verify(chatService).getSubmanagerChatIdByMainChatId(subInfo.getGroupId(), 10L);
        verify(vkChatClient, never()).sendText(any());
        verify(messageMapper, never()).toSendMessageDto(anyString(), anyLong(), anyLong(), any(GroupActor.class));
    }

    @Test
    void shouldLogIfSendFails() throws ApiException, ClientException {
        int postId = 123;
        long fromId = -subInfo.getGroupId();

        ChatEntity chat = new ChatEntity();
        chat.setChatId(10L);
        given(chatService.findChatsByBoundSubmanagerIdAndSubPostsEnabled(subInfo.getGroupId()))
                .willReturn(List.of(chat));
        given(chatService.getSubmanagerChatIdByMainChatId(subInfo.getGroupId(), 10L))
                .willReturn(100L);

        SendMessageDto message = mock(SendMessageDto.class);
        given(messageMapper.toSendMessageDto(anyString(), anyLong(), anyLong(), any(GroupActor.class)))
                .willReturn(message);
        doThrow(new ClientException("VK error")).when(vkChatClient).sendText(message);

        try (MockedStatic<ChatUtils> chatUtilsMock = mockStatic(ChatUtils.class)) {
            chatUtilsMock.when(() -> ChatUtils.buildGroupWallPostAsAttachment(subInfo.getGroupId(), postId))
                    .thenReturn("wall" + subInfo.getGroupId() + "_" + postId);

            submanagerActionService.sendNewSubPostToRequiredChats(subInfo, postId, fromId);

            verify(vkChatClient).sendText(message);
        }
    }
}