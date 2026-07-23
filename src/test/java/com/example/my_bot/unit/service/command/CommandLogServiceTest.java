package com.example.my_bot.unit.service.command;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.entity.CommandLogEntity;
import com.example.my_bot.repository.CommandLogRepository;
import com.example.my_bot.service.command.CommandLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.within;

@ExtendWith(MockitoExtension.class)
class CommandLogServiceTest {

    @Mock
    private CommandLogRepository commandLogRepository;

    @InjectMocks
    private CommandLogService commandLogService;

    private final long chatId = 1L;
    private final long fromId = 100L;
    private final String commandName = "testCommand";
    private final Command cmdAnnotation = mock(Command.class);

    @Test
    void shouldSaveNewCommandLog() {
        given(cmdAnnotation.mainCommandName()).willReturn(commandName);

        commandLogService.saveNewCommandLog(chatId, cmdAnnotation, fromId);

        ArgumentCaptor<CommandLogEntity> captor = ArgumentCaptor.forClass(CommandLogEntity.class);
        verify(commandLogRepository).save(captor.capture());
        CommandLogEntity saved = captor.getValue();
        assertThat(saved.getChatId()).isEqualTo(chatId);
        assertThat(saved.getFromId()).isEqualTo(fromId);
        assertThat(saved.getCommandName()).isEqualTo(commandName);
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void shouldGetLastNCommandLogs() {
        int quantity = 5;
        List<CommandLogEntity> expected = List.of(new CommandLogEntity(), new CommandLogEntity());
        given(commandLogRepository.findLastNCommandLogs(eq(chatId), any(PageRequest.class)))
                .willReturn(expected);

        List<CommandLogEntity> result = commandLogService.getLastNCommandLogs(chatId, quantity);

        assertThat(result).isSameAs(expected);
        verify(commandLogRepository).findLastNCommandLogs(eq(chatId), argThat(pageRequest ->
                pageRequest.getPageSize() == quantity && pageRequest.getPageNumber() == 0
        ));
    }

    @Test
    void shouldDeleteLogsOlderThanOneWeek() throws Exception {
        Method method = CommandLogService.class.getDeclaredMethod("deleteLogsOlderThanOneWeek");
        method.setAccessible(true);

        given(commandLogRepository.deleteOldRecords(any(Instant.class))).willReturn(10);

        method.invoke(commandLogService);

        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(commandLogRepository).deleteOldRecords(captor.capture());
        Instant deletedBefore = captor.getValue();
        Instant expected = Instant.now().minus(7, ChronoUnit.DAYS);
        assertThat(deletedBefore).isCloseTo(expected, within(1, ChronoUnit.SECONDS));
    }

    @Test
    void shouldHandleExceptionInDeleteLogs() throws Exception {
        Method method = CommandLogService.class.getDeclaredMethod("deleteLogsOlderThanOneWeek");
        method.setAccessible(true);

        doThrow(new RuntimeException("DB error")).when(commandLogRepository).deleteOldRecords(any(Instant.class));

        method.invoke(commandLogService);

        verify(commandLogRepository).deleteOldRecords(any(Instant.class));
    }
}