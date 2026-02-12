package org.twocache.twocachedemo.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.support.SimpleValueWrapper;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.lang.Nullable;
import org.twocache.twocachedemo.lock.RedisLock;
import org.twocache.twocachedemo.utils.ThreadTaskUtils;

import java.time.Duration;

/**
 * @author binghe
 * @version 1.0.0
 * @description 自定义Redis Cache
 */
@Slf4j
public class CustomizedRedisCache extends RedisCache {

    private final RedisOperations redisOperations;
    private final CacheSupport cacheSupport;
    private final long preloadSecondTime;
    private final long expirationSecondTime;
    private final String originalCacheName;

    public CustomizedRedisCache(String name, RedisCacheWriter redisCacheWriter, RedisCacheConfiguration redisCacheConfiguration,
                                long expiration, long preloadSecondTime, CacheSupport cacheSupport, RedisOperations redisOperations, String originalCacheName) {
        super(name, redisCacheWriter, expiration > 0 ? redisCacheConfiguration.entryTtl(Duration.ofSeconds(expiration)) : redisCacheConfiguration);
        this.redisOperations = redisOperations;
        this.expirationSecondTime = expiration;
        this.preloadSecondTime = preloadSecondTime;
        this.cacheSupport = cacheSupport;
        this.originalCacheName = originalCacheName;
    }

    @Override
    public ValueWrapper get(final Object key) {
        ValueWrapper valueWrapper = super.get(key);
        if (valueWrapper != null) {
            String cacheKeyStr = createCacheKey(key);
            refreshCache(key, cacheKeyStr);
        }
        return valueWrapper;
    }

    private void refreshCache(Object key, final String cacheKeyStr) {
        if (preloadSecondTime <= 0) {
            return;
        }

        Long ttl = redisOperations.getExpire(cacheKeyStr);
        if (ttl != null && ttl > 0 && ttl <= preloadSecondTime) {
            log.debug("缓存即将过期，准备刷新。key: {}, ttl: {}, preloadTime: {}", cacheKeyStr, ttl, preloadSecondTime);
            ThreadTaskUtils.run(() -> {
                // 使用 RedisLock 防止并发刷新
                RedisLock redisLock = new RedisLock((RedisTemplate) redisOperations, cacheKeyStr + "_lock");
                try {
                    if (redisLock.tryLock()) {
                        // 双重检查 TTL，防止重复刷新
                        Long currentTtl = redisOperations.getExpire(cacheKeyStr);
                        if (currentTtl != null && currentTtl > 0 && currentTtl <= preloadSecondTime) {
                            log.info("执行缓存刷新。key: {}", cacheKeyStr);
                            // 传递 originalCacheName (带时间后缀的名称) 以便 CacheSupportImpl 能获取到正确的 Cache 实例
                            cacheSupport.refreshCacheByKey(originalCacheName, cacheKeyStr);
                        }
                    }
                } catch (Exception e) {
                    log.error("刷新缓存失败", e);
                } finally {
                    redisLock.unlock();
                }
            });
        }
    }

    @Override
    public String createCacheKey(Object key) {
        // 暴露 protected 方法以便内部使用
        return super.createCacheKey(key);
    }

    public long getExpirationSecondTime() {
        return expirationSecondTime;
    }
}
