package com.desktopcat.server.knowledge.service;

import com.desktopcat.server.knowledge.dao.KnowledgeChatDO;
import com.desktopcat.server.knowledge.dao.KnowledgeMessageDO;
import com.desktopcat.server.knowledge.dto.KnowledgeMessageDto;
import com.desktopcat.server.knowledge.dto.RagConversationMessageDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/** 保存最近一百条对话消息；数据库仍然是完整历史的唯一事实来源。 */
@Component
@Profile("postgres")
public class KnowledgeMemoryStore {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeMemoryStore.class);
    private static final String PREFIX = "desktop-cat:knowledge-memory:";
    private static final int MAX_MESSAGES = 100;
    private static final Duration MISSING_TTL = Duration.ofSeconds(30);
    private static final Duration LOCK_TTL = Duration.ofSeconds(10);
    private static final DefaultRedisScript<Long> RELEASE_LOCK = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public KnowledgeMemoryStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public List<RagConversationMessageDto> loadHistory(
            long userId,
            KnowledgeChatDO chat,
            Supplier<List<KnowledgeMessageDO>> databaseLoader) {
        try {
            CacheSnapshot cached = readCache(userId, chat.getChatId());
            if (isCurrent(cached, chat.getVersion())) {
                return cached.messages();
            }

            String lockKey = lockKey(userId, chat.getChatId());
            String lockToken = UUID.randomUUID().toString();
            boolean acquired = Boolean.TRUE.equals(redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, lockToken, LOCK_TTL));
            if (!acquired) {
                for (int attempt = 0; attempt < 3; attempt++) {
                    waitForRebuild();
                    cached = readCache(userId, chat.getChatId());
                    if (isCurrent(cached, chat.getVersion())) {
                        return cached.messages();
                    }
                }
                return loadFromDatabase(databaseLoader);
            }

            try {
                cached = readCache(userId, chat.getChatId());
                if (isCurrent(cached, chat.getVersion())) {
                    return cached.messages();
                }
                List<RagConversationMessageDto> history = loadFromDatabase(databaseLoader);
                replaceCache(userId, chat.getChatId(), chat.getVersion(), history);
                return history;
            } finally {
                redisTemplate.execute(RELEASE_LOCK, List.of(lockKey), lockToken);
            }
        } catch (RuntimeException exception) {
            log.warn("event=knowledge_memory_load_failed userId={} chatId={} errorType={}",
                    userId, chat.getChatId(), exception.getClass().getSimpleName());
            return loadFromDatabase(databaseLoader);
        }
    }

    public void append(
            long userId,
            long chatId,
            int version,
            KnowledgeMessageDto userMessage,
            KnowledgeMessageDto assistantMessage) {
        String messagesKey = messagesKey(userId, chatId);
        String versionKey = versionKey(userId, chatId);
        Duration ttl = cacheTtl();
        try {
            if (version > 1) {
                String cachedVersion = redisTemplate.opsForValue().get(versionKey);
                if (!Integer.toString(version - 1).equals(cachedVersion)) {
                    // 缺少旧历史时不能只缓存本轮，否则会把不完整列表标记成最新版本。
                    delete(userId, chatId);
                    return;
                }
            }
            List<String> payloads = List.of(
                    writeCachedMessage(userMessage),
                    writeCachedMessage(assistantMessage));
            redisTemplate.executePipelined(new SessionCallback<>() {
                @Override
                public <K, V> Object execute(RedisOperations<K, V> operations) {
                    RedisOperations<String, String> stringOperations = stringOperations(operations);
                    stringOperations.opsForList().rightPushAll(messagesKey, payloads);
                    stringOperations.opsForList().trim(messagesKey, -MAX_MESSAGES, -1);
                    stringOperations.expire(messagesKey, ttl);
                    stringOperations.opsForValue().set(versionKey, Integer.toString(version), ttl);
                    return null;
                }
            });
        } catch (RuntimeException exception) {
            delete(userId, chatId);
            log.warn("event=knowledge_memory_append_failed userId={} chatId={} errorType={}",
                    userId, chatId, exception.getClass().getSimpleName());
        }
    }

    public boolean isMissing(long userId, long chatId) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(missingKey(userId, chatId)));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public void invalidateDeleted(long userId, long chatId) {
        try {
            redisTemplate.executePipelined(new SessionCallback<>() {
                @Override
                public <K, V> Object execute(RedisOperations<K, V> operations) {
                    RedisOperations<String, String> stringOperations = stringOperations(operations);
                    stringOperations.delete(List.of(
                            messagesKey(userId, chatId), versionKey(userId, chatId)));
                    stringOperations.opsForValue().set(missingKey(userId, chatId), "1", MISSING_TTL);
                    return null;
                }
            });
        } catch (RuntimeException exception) {
            log.warn("event=knowledge_memory_invalidate_failed userId={} chatId={} errorType={}",
                    userId, chatId, exception.getClass().getSimpleName());
        }
    }

    public void markMissing(long userId, long chatId) {
        try {
            redisTemplate.opsForValue().set(missingKey(userId, chatId), "1", MISSING_TTL);
        } catch (RuntimeException exception) {
            log.debug("event=knowledge_memory_missing_marker_failed userId={} chatId={}",
                    userId, chatId);
        }
    }

    private CacheSnapshot readCache(long userId, long chatId) {
        String messagesKey = messagesKey(userId, chatId);
        String versionKey = versionKey(userId, chatId);
        Duration ttl = cacheTtl();
        List<Object> results = redisTemplate.executePipelined(new SessionCallback<>() {
            @Override
            public <K, V> Object execute(RedisOperations<K, V> operations) {
                RedisOperations<String, String> stringOperations = stringOperations(operations);
                stringOperations.opsForList().range(messagesKey, 0, -1);
                stringOperations.opsForValue().get(versionKey);
                stringOperations.expire(messagesKey, ttl);
                stringOperations.expire(versionKey, ttl);
                return null;
            }
        });
        if (results.size() < 2 || !(results.get(0) instanceof List<?> payloads)
                || !(results.get(1) instanceof String versionValue)) {
            return null;
        }

        List<RagConversationMessageDto> messages = new ArrayList<>();
        for (Object payload : payloads) {
            if (!(payload instanceof String text)) {
                return null;
            }
            try {
                CachedMessage message = objectMapper.readValue(text, CachedMessage.class);
                messages.add(new RagConversationMessageDto(message.role(), message.content()));
            } catch (JsonProcessingException exception) {
                delete(userId, chatId);
                return null;
            }
        }
        try {
            return new CacheSnapshot(Integer.parseInt(versionValue), List.copyOf(messages));
        } catch (NumberFormatException exception) {
            delete(userId, chatId);
            return null;
        }
    }

    private void replaceCache(
            long userId,
            long chatId,
            int version,
            List<RagConversationMessageDto> history) {
        String messagesKey = messagesKey(userId, chatId);
        String versionKey = versionKey(userId, chatId);
        Duration ttl = cacheTtl();
        List<String> payloads = new ArrayList<>();
        for (int index = 0; index < history.size(); index++) {
            RagConversationMessageDto message = history.get(index);
            payloads.add(writeCachedMessage(new CachedMessage(
                    (long) index, message.role(), message.content())));
        }
        redisTemplate.executePipelined(new SessionCallback<>() {
            @Override
            public <K, V> Object execute(RedisOperations<K, V> operations) {
                RedisOperations<String, String> stringOperations = stringOperations(operations);
                stringOperations.delete(messagesKey);
                if (!payloads.isEmpty()) {
                    stringOperations.opsForList().rightPushAll(messagesKey, payloads);
                    stringOperations.opsForList().trim(messagesKey, -MAX_MESSAGES, -1);
                    stringOperations.expire(messagesKey, ttl);
                }
                // 版本最后写入；部分失败的缓存不会被后续请求当成有效上下文。
                stringOperations.opsForValue().set(versionKey, Integer.toString(version), ttl);
                return null;
            }
        });
    }

    private List<RagConversationMessageDto> loadFromDatabase(
            Supplier<List<KnowledgeMessageDO>> databaseLoader) {
        List<KnowledgeMessageDO> newestFirst = new ArrayList<>(databaseLoader.get());
        Collections.reverse(newestFirst);
        return newestFirst.stream()
                .map(message -> new RagConversationMessageDto(
                        message.getRole(), message.getContent()))
                .toList();
    }

    private void delete(long userId, long chatId) {
        try {
            redisTemplate.delete(List.of(
                    messagesKey(userId, chatId),
                    versionKey(userId, chatId)));
        } catch (RuntimeException exception) {
            log.debug("event=knowledge_memory_delete_failed userId={} chatId={}", userId, chatId);
        }
    }

    private boolean isCurrent(CacheSnapshot snapshot, Integer databaseVersion) {
        return snapshot != null && databaseVersion != null
                && snapshot.version() == databaseVersion;
    }

    private String writeCachedMessage(KnowledgeMessageDto message) {
        return writeCachedMessage(new CachedMessage(
                message.messageId(), message.role(), message.content()));
    }

    private String writeCachedMessage(CachedMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Knowledge message could not be cached.", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private RedisOperations<String, String> stringOperations(RedisOperations<?, ?> operations) {
        return (RedisOperations<String, String>) operations;
    }

    private void waitForRebuild() {
        try {
            Thread.sleep(50);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Knowledge memory rebuild was interrupted.", exception);
        }
    }

    private Duration cacheTtl() {
        return Duration.ofHours(24)
                .plusMinutes(ThreadLocalRandom.current().nextLong(121));
    }

    private String messagesKey(long userId, long chatId) {
        return PREFIX + userId + ':' + chatId + ":messages";
    }

    private String versionKey(long userId, long chatId) {
        return PREFIX + userId + ':' + chatId + ":version";
    }

    private String missingKey(long userId, long chatId) {
        return PREFIX + userId + ':' + chatId + ":missing";
    }

    private String lockKey(long userId, long chatId) {
        return PREFIX + userId + ':' + chatId + ":lock";
    }

    private record CachedMessage(long messageId, String role, String content) {
    }

    private record CacheSnapshot(int version, List<RagConversationMessageDto> messages) {
    }
}
