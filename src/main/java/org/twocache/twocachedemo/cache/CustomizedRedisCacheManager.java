package org.twocache.twocachedemo.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.util.CollectionUtils;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author binghe
 * @version 1.0.0
 * @description 自定义的redis缓存管理器
 */
@Slf4j
public class CustomizedRedisCacheManager extends RedisCacheManager {

    private final RedisOperations redisOperations;
    private final RedisCacheWriter redisCacheWriter;
    private final CacheSupport cacheSupport;
    
    private volatile Map<String, CacheTime> cacheTimes = new ConcurrentHashMap<>();

    public CustomizedRedisCacheManager(RedisCacheWriter cacheWriter, RedisCacheConfiguration defaultCacheConfiguration, RedisOperations redisOperations, CacheSupport cacheSupport) {
        super(cacheWriter, defaultCacheConfiguration);
        this.redisCacheWriter = cacheWriter;
        this.redisOperations = redisOperations;
        this.cacheSupport = cacheSupport;
    }

    @Override
    protected RedisCache createRedisCache(String name, RedisCacheConfiguration cacheConfig) {
        log.info("开始创建 RedisCache: {}", name);
        String cacheKey = name;
        long expiration = 0;
        long preload = 0;

        if (cacheSupport != null) {
            // 解析缓存名称中的时间信息
            Map<String, CacheTime> map = cacheSupport.getCacheTimes(name);
            if (!CollectionUtils.isEmpty(map)) {
                cacheTimes.putAll(map);
                // 获取解析后的真实缓存名称（去除时间后缀）
                cacheKey = cacheSupport.getCacheKey(name);
                CacheTime cacheTime = map.get(cacheKey);
                if (cacheTime != null) {
                    expiration = cacheTime.getExpirationSecondTime();
                    preload = cacheTime.getPreloadSecondTime();
                    log.info("解析到缓存配置 - name: {}, cacheKey: {}, expiration: {}, preload: {}", name, cacheKey, expiration, preload);
                } else {
                    log.warn("解析缓存时间失败，cacheTime 为 null - name: {}", name);
                }
            } else {
                log.warn("解析缓存时间失败，map 为空 - name: {}", name);
            }
        } else {
            log.error("CacheSupport 为 null，将使用默认配置 - name: {}", name);
        }

        // 如果配置了过期时间，覆盖默认配置
        if (expiration > 0) {
            cacheConfig = cacheConfig.entryTtl(Duration.ofSeconds(expiration));
        }

        // 传递原始名称 name 给 CustomizedRedisCache
        return new CustomizedRedisCache(cacheKey, redisCacheWriter, cacheConfig, expiration, preload, cacheSupport, redisOperations, name);
    }
    
    public void setCacheTimes(Map<String, CacheTime> cacheTimes) {
        if (!CollectionUtils.isEmpty(cacheTimes)) {
            this.cacheTimes.putAll(cacheTimes);
        }
    }
}
