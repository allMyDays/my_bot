package com.example.my_bot.unit.service;

import com.example.my_bot.config.CaffeineCacheManager;
import com.example.my_bot.dto.user.GlobalUserDetailsDto;
import com.example.my_bot.dto.user.UserFullNameInEachCase;
import com.example.my_bot.entity.GlobalUserEntity;
import com.example.my_bot.enumeration.user.NameCase;
import com.example.my_bot.exception.user.GlobalGlobalUserDoesNotHaveRequiredBoundChatException;
import com.example.my_bot.exception.user.GlobalGlobalUserNotFoundException;
import com.example.my_bot.mapper.FullNameMapper;
import com.example.my_bot.mapper.GlobalUserMapper;
import com.example.my_bot.repository.GlobalUserRepository;
import com.example.my_bot.service.GlobalUserService;
import com.example.my_bot.service.MemberService;
import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GlobalUserServiceTest {

    @Mock
    private GlobalUserRepository globalUserRepository;

    @Mock
    private CaffeineCacheManager cacheManager;

    @Mock
    private GlobalUserMapper globalUserMapper;

    @Mock
    private MemberService memberService;

    @Mock
    private FullNameMapper fullNameMapper;

    @Mock
    private Cache<Long, GlobalUserDetailsDto> userDetailsCache;

    @Mock
    private Cache<Long, ConcurrentHashMap<NameCase, String>> fullNameCache;

    @InjectMocks
    private GlobalUserService globalUserService;

    private final long userId = 1L;
    private final long chatId = 100L;

    @BeforeEach
    void setUp() {
        lenient().when(cacheManager.getGlobalUserDetailsCache()).thenReturn(userDetailsCache);
        lenient().when(cacheManager.getFullNameCache()).thenReturn(fullNameCache);
    }


    @Test
    void getOrCreateUser_whenUserInCache_shouldReturnFromCache() {
        GlobalUserDetailsDto cachedDto = new GlobalUserDetailsDto();
        when(userDetailsCache.get(eq(userId), any())).thenReturn(cachedDto);

        GlobalUserDetailsDto result = globalUserService.getOrCreateUser(userId);

        assertSame(cachedDto, result);
        verify(globalUserRepository, never()).findById(anyLong());
        verify(globalUserRepository, never()).save(any());
    }

    @Test
    void getOrCreateUser_whenUserNotInCacheAndExistsInDb_shouldLoadAndCache() {
        GlobalUserEntity entity = new GlobalUserEntity(userId);
        GlobalUserDetailsDto dto = new GlobalUserDetailsDto();
        when(globalUserRepository.findById(userId)).thenReturn(Optional.of(entity));
        when(globalUserMapper.toUserDetailsDto(entity)).thenReturn(dto);

        when(userDetailsCache.get(eq(userId), any())).thenAnswer(invocation -> {
            Function<Long, GlobalUserDetailsDto> loader = invocation.getArgument(1);
            GlobalUserDetailsDto result = loader.apply(userId);
            userDetailsCache.put(userId, result); // <- добавляем сохранение
            return result;
        });

        GlobalUserDetailsDto result = globalUserService.getOrCreateUser(userId);

        assertSame(dto, result);
        verify(globalUserRepository).findById(userId);
        verify(globalUserRepository, never()).save(any());
        verify(globalUserMapper).toUserDetailsDto(entity);
        verify(userDetailsCache).put(userId, dto); // теперь проверка проходит
    }

    @Test
    void getOrCreateUser_whenUserNotInCacheAndNotInDb_shouldCreateNewAndCache() {
        GlobalUserEntity newEntity = new GlobalUserEntity(userId);
        GlobalUserDetailsDto dto = new GlobalUserDetailsDto();
        when(globalUserRepository.findById(userId)).thenReturn(Optional.empty());
        when(globalUserRepository.save(any(GlobalUserEntity.class))).thenReturn(newEntity);
        when(globalUserMapper.toUserDetailsDto(newEntity)).thenReturn(dto);

        when(userDetailsCache.get(eq(userId), any())).thenAnswer(invocation -> {
            Function<Long, GlobalUserDetailsDto> loader = invocation.getArgument(1);
            GlobalUserDetailsDto result = loader.apply(userId);
            userDetailsCache.put(userId, result);
            return result;
        });

        GlobalUserDetailsDto result = globalUserService.getOrCreateUser(userId);

        assertSame(dto, result);
        verify(globalUserRepository).findById(userId);
        verify(globalUserRepository).save(argThat(entity -> entity.getUserId() == userId));
        verify(globalUserMapper).toUserDetailsDto(newEntity);
        verify(userDetailsCache).put(userId, dto);
    }

    @Test
    void bindChatToUser_whenUserExists_shouldBindAndUpdateCache() {
        GlobalUserEntity entity = new GlobalUserEntity(userId);
        GlobalUserDetailsDto dto = new GlobalUserDetailsDto();
        when(globalUserRepository.findById(userId)).thenReturn(Optional.of(entity));
        when(globalUserMapper.toUserDetailsDto(entity)).thenReturn(dto);

        globalUserService.bindChatToUser(chatId, userId);

        assertEquals(chatId, entity.getBoundChat());
        verify(userDetailsCache).put(userId, dto);
        verify(globalUserMapper).toUserDetailsDto(entity);
    }

    @Test
    void bindChatToUser_whenUserNotFound_shouldThrowException() {
        when(globalUserRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(GlobalGlobalUserNotFoundException.class,
                () -> globalUserService.bindChatToUser(chatId, userId));
        verify(userDetailsCache, never()).put(anyLong(), any());
    }


    @Test
    void unBindChatFromUser_whenFromIdEqualsUserToUnbind_shouldNotCheckMemberInteraction() {
        GlobalUserEntity entity = new GlobalUserEntity(userId);
        entity.setBoundChat(chatId);
        when(globalUserRepository.findById(userId)).thenReturn(Optional.of(entity));

        globalUserService.unBindChatFromUser(chatId, userId, userId);

        assertNull(entity.getBoundChat());
        verify(memberService, never()).checkMemberInteractionAbility(anyLong(), anyLong(), anyLong(), anyBoolean());
        verify(userDetailsCache).put(userId, globalUserMapper.toUserDetailsDto(entity));
    }

    @Test
    void unBindChatFromUser_whenFromIdDifferent_shouldCheckMemberInteraction() {
        long fromId = 2L;
        GlobalUserEntity entity = new GlobalUserEntity(userId);
        entity.setBoundChat(chatId);
        when(globalUserRepository.findById(userId)).thenReturn(Optional.of(entity));

        globalUserService.unBindChatFromUser(chatId, fromId, userId);

        verify(memberService).checkMemberInteractionAbility(chatId, fromId, userId, true);
        assertNull(entity.getBoundChat());
        verify(userDetailsCache).put(userId, globalUserMapper.toUserDetailsDto(entity));
    }

    @Test
    void unBindChatFromUser_whenUserNotFound_shouldThrowException() {
        when(globalUserRepository.findById(userId)).thenReturn(Optional.empty());
        assertThrows(GlobalGlobalUserNotFoundException.class,
                () -> globalUserService.unBindChatFromUser(chatId, userId, userId));
        verify(memberService, never()).checkMemberInteractionAbility(anyLong(), anyLong(), anyLong(), anyBoolean());
    }

    @Test
    void unBindChatFromUser_whenBoundChatIsNull_shouldThrowException() {
        GlobalUserEntity entity = new GlobalUserEntity(userId);
        when(globalUserRepository.findById(userId)).thenReturn(Optional.of(entity));
        assertThrows(GlobalGlobalUserDoesNotHaveRequiredBoundChatException.class,
                () -> globalUserService.unBindChatFromUser(chatId, userId, userId));
        verify(memberService, never()).checkMemberInteractionAbility(anyLong(), anyLong(), anyLong(), anyBoolean());
    }

    @Test
    void unBindChatFromUser_whenBoundChatDoesNotMatch_shouldThrowException() {
        GlobalUserEntity entity = new GlobalUserEntity(userId);
        entity.setBoundChat(999L);
        when(globalUserRepository.findById(userId)).thenReturn(Optional.of(entity));
        assertThrows(GlobalGlobalUserDoesNotHaveRequiredBoundChatException.class,
                () -> globalUserService.unBindChatFromUser(chatId, userId, userId));
        verify(memberService, never()).checkMemberInteractionAbility(anyLong(), anyLong(), anyLong(), anyBoolean());
    }


    @Test
    void findUserIdsByBoundChat_shouldDelegateToRepository() {
        Set<Long> expected = Set.of(1L, 2L);
        when(globalUserRepository.findUserIdsByBoundChat(chatId)).thenReturn(expected);

        Set<Long> result = globalUserService.findUserIdsByBoundChat(chatId);

        assertEquals(expected, result);
        verify(globalUserRepository).findUserIdsByBoundChat(chatId);
    }


    @Test
    void getUserFullNameInRequiredCase_shouldCallGetUserFullNamesInRequiredCaseWithSingleUser() {
        Set<Long> users = Set.of(userId);
        Map<Long, String> expected = Map.of(userId, "Test User");
        GlobalUserService spy = spy(globalUserService);
        doReturn(expected).when(spy).getUserFullNamesInRequiredCase(eq(users), any(NameCase.class));

        String result = spy.getUserFullNameInRequiredCase(userId, NameCase.NOMINATIVE);

        assertEquals("Test User", result);
        verify(spy).getUserFullNamesInRequiredCase(users, NameCase.NOMINATIVE);
    }


    @Test
    void getUserFullNamesInRequiredCase_whenAllInCacheAndHaveRequiredCase_shouldReturnFromCache() {
        Set<Long> users = Set.of(userId);
        ConcurrentHashMap<NameCase, String> nameMap = new ConcurrentHashMap<>();
        nameMap.put(NameCase.NOMINATIVE, "John");
        nameMap.put(NameCase.GENITIVE, "John's");
        when(fullNameCache.getIfPresent(userId)).thenReturn(nameMap);

        Map<Long, String> result = globalUserService.getUserFullNamesInRequiredCase(users, NameCase.GENITIVE);

        assertEquals(Map.of(userId, "John's"), result);
        verify(globalUserRepository, never()).findAllById(any());
        verify(fullNameCache, never()).putAll(any());
    }

    @Test
    void getUserFullNamesInRequiredCase_whenInCacheOnlyNominative_shouldReturnNominativeEvenIfRequiredOther() {
        Set<Long> users = Set.of(userId);
        ConcurrentHashMap<NameCase, String> nameMap = new ConcurrentHashMap<>();
        nameMap.put(NameCase.NOMINATIVE, "John");
        when(fullNameCache.getIfPresent(userId)).thenReturn(nameMap);

        Map<Long, String> result = globalUserService.getUserFullNamesInRequiredCase(users, NameCase.GENITIVE);

        assertEquals(Map.of(userId, "John"), result);
        verify(globalUserRepository, never()).findAllById(any());
    }

    @Test
    void getUserFullNamesInRequiredCase_whenInCacheNoNames_shouldReturnMention() {
        Set<Long> users = Set.of(userId);
        ConcurrentHashMap<NameCase, String> nameMap = new ConcurrentHashMap<>(); // пустой
        when(fullNameCache.getIfPresent(userId)).thenReturn(nameMap);

        Map<Long, String> result = globalUserService.getUserFullNamesInRequiredCase(users, NameCase.NOMINATIVE);

        assertTrue(result.containsKey(userId));
        assertNotNull(result.get(userId));
        verify(globalUserRepository, never()).findAllById(any());
    }

    @Test
    void getUserFullNamesInRequiredCase_whenSomeMissingInCache_shouldLoadFromDb() {
        Set<Long> users = Set.of(1L, 2L);
        ConcurrentHashMap<NameCase, String> cacheMap1 = new ConcurrentHashMap<>();
        cacheMap1.put(NameCase.NOMINATIVE, "User1");
        when(fullNameCache.getIfPresent(1L)).thenReturn(cacheMap1);
        when(fullNameCache.getIfPresent(2L)).thenReturn(null);

        GlobalUserEntity entity2 = new GlobalUserEntity(2L);
        when(globalUserRepository.findAllById(Set.of(2L))).thenReturn(List.of(entity2));
        when(fullNameMapper.mapNames(entity2, NameCase.NOMINATIVE)).thenReturn("User2");
        when(fullNameMapper.mapNames(entity2, NameCase.GENITIVE)).thenReturn("User2's");

        Map<Long, String> result = globalUserService.getUserFullNamesInRequiredCase(users, NameCase.GENITIVE);

        assertEquals("User1", result.get(1L));
        assertEquals("User2's", result.get(2L));

        ArgumentCaptor<Map<Long, ConcurrentHashMap<NameCase, String>>> captor = ArgumentCaptor.forClass(Map.class);
        verify(fullNameCache).putAll(captor.capture());
        Map<Long, ConcurrentHashMap<NameCase, String>> putMap = captor.getValue();
        assertTrue(putMap.containsKey(2L));
        ConcurrentHashMap<NameCase, String> loadedMap = putMap.get(2L);
        assertEquals("User2", loadedMap.get(NameCase.NOMINATIVE));
        assertEquals("User2's", loadedMap.get(NameCase.GENITIVE));
    }

    @Test
    void getUserFullNamesInRequiredCase_whenUserNotInDb_shouldReturnMentionAndCacheEmpty() {
        Set<Long> users = Set.of(userId);
        when(fullNameCache.getIfPresent(userId)).thenReturn(null);
        when(globalUserRepository.findAllById(Set.of(userId))).thenReturn(Collections.emptyList());

        Map<Long, String> result = globalUserService.getUserFullNamesInRequiredCase(users, NameCase.NOMINATIVE);

        assertNotNull(result.get(userId));
        ArgumentCaptor<Map<Long, ConcurrentHashMap<NameCase, String>>> captor = ArgumentCaptor.forClass(Map.class);
        verify(fullNameCache).putAll(captor.capture());
        Map<Long, ConcurrentHashMap<NameCase, String>> putMap = captor.getValue();
        assertTrue(putMap.containsKey(userId));
        assertTrue(putMap.get(userId).isEmpty());
    }

    @Test
    void putFullNamesToTheDataBase_shouldSaveAndInvalidateCache() {
        List<UserFullNameInEachCase> namesToSave = new ArrayList<>();
        UserFullNameInEachCase name1 = new UserFullNameInEachCase();
        name1.setUserId(userId);
        namesToSave.add(name1);
        UserFullNameInEachCase name2 = new UserFullNameInEachCase();
        name2.setUserId(2L);
        namesToSave.add(name2);

        GlobalUserEntity existing1 = new GlobalUserEntity(userId);
        when(globalUserRepository.findAllById(Set.of(userId, 2L))).thenReturn(List.of(existing1));

        when(fullNameMapper.mapNames(any(GlobalUserEntity.class), any(UserFullNameInEachCase.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        globalUserService.putFullNamesToTheDataBase(namesToSave);

        verify(fullNameCache).invalidate(userId);
        verify(fullNameCache).invalidate(2L);
        ArgumentCaptor<Iterable<GlobalUserEntity>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(globalUserRepository).saveAll(captor.capture());
        Iterable<GlobalUserEntity> saved = captor.getValue();
        List<GlobalUserEntity> list = new ArrayList<>();
        saved.forEach(list::add);
        assertEquals(2, list.size());
        assertTrue(list.stream().anyMatch(e -> e.getUserId() == userId));
        assertTrue(list.stream().anyMatch(e -> e.getUserId() == 2L));

        verify(fullNameMapper).mapNames(existing1, name1);
        verify(fullNameMapper).mapNames(any(GlobalUserEntity.class), eq(name2));
    }

    @Test
    void putFullNamesToTheDataBase_whenSomeEntitiesNew_shouldCreateAndSave() {
        List<UserFullNameInEachCase> namesToSave = new ArrayList<>();
        UserFullNameInEachCase name = new UserFullNameInEachCase();
        name.setUserId(userId);
        namesToSave.add(name);

        when(globalUserRepository.findAllById(Set.of(userId))).thenReturn(Collections.emptyList());

        when(fullNameMapper.mapNames(any(GlobalUserEntity.class), any(UserFullNameInEachCase.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        globalUserService.putFullNamesToTheDataBase(namesToSave);

        ArgumentCaptor<Iterable<GlobalUserEntity>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(globalUserRepository).saveAll(captor.capture());
        Iterable<GlobalUserEntity> saved = captor.getValue();
        GlobalUserEntity newEntity = saved.iterator().next();
        assertEquals(userId, newEntity.getUserId());

        verify(fullNameMapper).mapNames(newEntity, name);
        verify(fullNameCache).invalidate(userId);
    }
}

