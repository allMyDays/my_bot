package com.example.my_bot.service;

import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.command.CommandRegistry;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.cooldown.CooldownResult;
import com.example.my_bot.exception.command.UserCommandNotFoundException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.example.my_bot.enumeration.CooldownCacheKeyBuilder.DEFAULT_COOLDOWN;

@Slf4j
@Service
@RequiredArgsConstructor
public class CooldownService {

    private final CommandRegistry commandRegistry;

    private final static int MILLISECONDS_BETWEEN_SENDING_COOLDOWN_MESSAGE_TO_USER = 30_000;

    private static class CooldownData {
         final Deque<Long> calls;
         final long cooldownPeriodInSeconds;
         long lastSentCDMessageInMillis;

        CooldownData(long cooldownPeriodInSeconds) {
            this.calls = new ArrayDeque<>();
            this.cooldownPeriodInSeconds = cooldownPeriodInSeconds;
            lastSentCDMessageInMillis =0;

        }
    }
    private final Cache<String, CooldownData> cooldownCache = Caffeine.newBuilder()
            .maximumSize(50_000)
            .expireAfter(new Expiry<String, CooldownData>() {
                @Override
                public long expireAfterCreate(String key, CooldownData data, long currentTime) {
                    return TimeUnit.SECONDS.toNanos(data.cooldownPeriodInSeconds);
                }

                @Override
                public long expireAfterUpdate(String key, CooldownData data, long currentTime, long currentDuration) {
                    // при добавлении вызова сбрасываю ttl
                    return TimeUnit.SECONDS.toNanos(data.cooldownPeriodInSeconds);
                }

                @Override
                public long expireAfterRead(String key, CooldownData data, long currentTime, long currentDuration) {
                    return currentDuration;
                }
            })
            .build();



    public CooldownResult tryConsume(long chatId, long userId, @NonNull String normalizedCommand) {
        Optional<ChatCommand> chatCommand = commandRegistry.getCommand(normalizedCommand);
        if (chatCommand.isEmpty()) {
            log.error("chat {} error: ChatCommand not found for command '{}'", chatId, normalizedCommand);
            throw new UserCommandNotFoundException(normalizedCommand);
        }
        CommandCooldown cooldown = chatCommand.get().getCooldown();
        if (cooldown == null) {
            log.error("chat {} error: CommandCooldown class not found for command'{}'", chatId, normalizedCommand);
            throw new IllegalStateException("CommandCooldown class not found for command "+normalizedCommand);
        }
        String key = DEFAULT_COOLDOWN.buildKey(chatId , userId, normalizedCommand);

        int maxUses = cooldown.getMaxUses();
        long now = System.currentTimeMillis();
        long cooldownPeriodMillis = cooldown.getSeconds() * 1000L;

        CooldownResult resultToReturn = new CooldownResult();

        CooldownData newData = cooldownCache.asMap().compute(key, (k, existing) -> {
            CooldownData data = (existing != null) ? existing : new CooldownData(cooldown.getSeconds());
            Deque<Long> deque = data.calls;
            long lastSentCDMessage = data.lastSentCDMessageInMillis;

            // удаляю устаревшие вызовы
            while (!deque.isEmpty() && deque.peekFirst() < now - cooldownPeriodMillis) {
                deque.pollFirst();
            }

            if (deque.size() < maxUses) {
                deque.addLast(now);
            }else{
                if(lastSentCDMessage==0||(now-lastSentCDMessage)>MILLISECONDS_BETWEEN_SENDING_COOLDOWN_MESSAGE_TO_USER){
                    data.lastSentCDMessageInMillis =now;
                }
            }
            return data;
        });
        // был ли добавлен текущий вызов
        if (!newData.calls.isEmpty() && newData.calls.peekLast().equals(now)) {
            resultToReturn.setCanExecuteCommand(true);
            return resultToReturn;
        } else {
            // лимит исчерпан, жду когда освободится самый старый вызов
            Long oldest = newData.calls.peekFirst();
            if (oldest == null) {
                resultToReturn.setCanExecuteCommand(true);
                return resultToReturn;
            } resultToReturn.setCanExecuteCommand(false);
            if(newData.lastSentCDMessageInMillis ==now){
                resultToReturn.setCanSendCDMessageToUser(true);
            }

            long remaining = oldest + cooldownPeriodMillis - now;
            resultToReturn.setLeftCooldownSeconds(remaining > 0 ? remaining / 1000 : 0);
            return resultToReturn;
        }
    }
}

