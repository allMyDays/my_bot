package com.example.my_bot.service;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@AllArgsConstructor
public class RedisService {

    private StringRedisTemplate redisTemplate;

    public void save(@NonNull String key, @NonNull String value) {
        redisTemplate.opsForValue().set(key, value);
    }

    public void saveTemp(@NonNull String key, @NonNull String value, long seconds) {
        redisTemplate.opsForValue().set(key, value, Duration.ofSeconds(seconds));
    }

    public Optional<String> get(@NonNull String key) {

        String value = redisTemplate.opsForValue().get(key);

        return Optional.ofNullable(value==null?null:value.trim());
    }
    public Optional<String> getAndDelete(@NonNull String key) {

        String value = redisTemplate.opsForValue().getAndDelete(key);

        return Optional.ofNullable(value==null?null:value.trim());
    }

    public Map<String, String> getHash(@NonNull String mainKey){
        Map<String, String> map = new HashMap<>();

        redisTemplate.opsForHash().entries(mainKey)
                .forEach((k, v) -> map.put(k.toString(), v.toString()));

        return map;
    }

    public void setHash(@NonNull String key, @NonNull Map<String, String> map, long seconds) {
        redisTemplate.opsForHash().putAll(key, map);
        redisTemplate.expire(key, seconds, TimeUnit.SECONDS);
    }

    public void delete(@NonNull String key) {
        redisTemplate.delete(key);
    }

    public void deleteAll(@NonNull List<String> keys) {
        redisTemplate.delete(keys);
    }






}
