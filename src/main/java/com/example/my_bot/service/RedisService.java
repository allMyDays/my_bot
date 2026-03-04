package com.example.my_bot.service;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
public class RedisService {

    private StringRedisTemplate stringRedisTemplate;

    public void save(@NonNull String key, @NonNull String value) {
        stringRedisTemplate.opsForValue().set(key, value);
    }

    public void saveTemp(@NonNull String key, @NonNull String value, long seconds) {
        stringRedisTemplate.opsForValue().set(key, value, Duration.ofSeconds(seconds));
    }

    public Optional<String> get(@NonNull String key) {

        String value = stringRedisTemplate.opsForValue().get(key);

        return Optional.ofNullable(value==null?null:value.trim());
    }
    public Optional<String> getAndDelete(@NonNull String key) {

        String value = stringRedisTemplate.opsForValue().getAndDelete(key);

        return Optional.ofNullable(value==null?null:value.trim());
    }


    public void delete(@NonNull String key) {
        stringRedisTemplate.delete(key);
    }

    public void deleteAll(@NonNull List<String> keys) {
        stringRedisTemplate.delete(keys);
    }






}
