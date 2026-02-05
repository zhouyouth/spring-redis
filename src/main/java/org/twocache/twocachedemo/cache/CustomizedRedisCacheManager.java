package org.twocache.twocachedemo.cache;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.CacheKeyPrefix;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.twocache.twocachedemo.config.CacheRedisConfig;
import org.twocache.twocachedemo.constants.CacheConstants;
import org.twocache.twocachedemo.context.SpringContextWrapper;
import org.twocache.twocachedemo.utils.ReflectionUtils;

import java.time.Duration;
import java.util.Collection;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author binghe
 * @version 1.0.0
 * @description
 * 自定义的redis缓存管理器
 * 支持方法上配置过期时间
 * 支持热加载缓存：缓存即将过期时主动刷新缓存
 */
@Slf4j
public class CustomizedRedisCacheManager extends RedisCacheManager {
    public final RedisOperations redisOperations;
//    private final RedisConnectionFactory connectionFactory;
    private RedisCacheManager redisCacheManager;
    private RedisCacheConfiguration redisCacheConfiguration;

    private CacheSupport getCacheSupport() {
        String contextKey = SpringContextWrapper.getContextKey(CacheSupport.class);
        return SpringContextWrapper.getBean(contextKey, CacheSupport.class);
    }

    // 0 - never expire
    private long defaultExpiration = 0;

    private volatile Map<String, CacheTime> cacheTimes = null;

    public CustomizedRedisCacheManager(RedisOperations redisOperations, Collection<String> cacheNames, CacheSupport cacheSupport, RedisOperations redisOperations1) {
        super(RedisCacheWriter.lockingRedisCacheWriter(null),
                RedisCacheConfiguration.defaultCacheConfig(),
                cacheNames.toArray(new String[0]));
        this.redisOperations = redisOperations1;
    }

    public CustomizedRedisCacheManager(RedisCacheWriter cacheWriter, RedisOperations redisOperations, RedisCacheConfiguration  redisCacheConfiguration) {
//        RedisCacheConfiguration defaultCacheConfiguration = redisCacheConfiguration.serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
//                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(RedisSerializer.json()));
        super(cacheWriter, redisCacheConfiguration);
        this.redisOperations = redisOperations;
        this.redisCacheConfiguration = redisCacheConfiguration;
    }

    public RedisCacheManager getInstance() {
        if (redisCacheManager == null) {
            redisCacheManager = SpringContextWrapper.getBean(SpringContextWrapper.getContextKey(RedisCacheManager.class), RedisCacheManager.class);
        }
        return redisCacheManager;
        // 返回当前实例，因为当前类就是RedisCacheManager的子类
    }

    /**
     * 获取过期时间
     * @param name 缓存名称
     * @return 过期时间
     */
    public long getExpirationSecondTime(String name) {
        if (StringUtils.isEmpty(name)) {
            return 0;
        }

        CacheTime cacheTime = null;
        if (!CollectionUtils.isEmpty(cacheTimes)) {
            cacheTime = cacheTimes.get(name);
        }
        Long expiration = cacheTime != null ? cacheTime.getExpirationSecondTime() : defaultExpiration;
        return expiration < 0 ? 0 : expiration;
    }

    /**
     * 获取自动刷新时间
     * @param name 缓存名称
     * @return 自动刷新时间
     */
    private long getPreloadSecondTime(String name) {
        // 自动刷新时间，默认是0
        CacheTime cacheTime = null;
        if (!CollectionUtils.isEmpty(cacheTimes)) {
            cacheTime = cacheTimes.get(name);
        }
        Long preloadSecondTime = cacheTime != null ? cacheTime.getPreloadSecondTime() : 0;
        return preloadSecondTime < 0 ? 0 : preloadSecondTime;
    }

    /**
     * 创建缓存
     * @param cacheName 缓存名称
     * @return CustomizedRedisCache对象
     */
    @Override
    public CustomizedRedisCache getMissingCache(String cacheName) {
        CacheSupport cacheSupport = this.getCacheSupport();
        String cacheKey = cacheSupport.getCacheKey(cacheName);
        Map<String, CacheTime> map = cacheSupport.getCacheTimes(cacheName);
        log.debug("getMissingCache方法调用cacheSupport.getCacheTimes()方法获取的结果Map为===>>>" + JSONObject.toJSONString(map));
        //处理cacheTimes和Map
        this.putMapToCacheTimes(map);
        // 有效时间，初始化获取默认的有效时间
        Long expirationSecondTime = getExpirationSecondTime(cacheKey);
        // 自动刷新时间，默认是0
        Long preloadSecondTime = getPreloadSecondTime(cacheKey);

        log.debug("目前cacheTimes中存储的数据为：{}", JSONObject.toJSONString(cacheTimes));
        log.debug("缓存 cacheName：{}，过期时间:{}, 自动刷新时间:{}", cacheKey, expirationSecondTime, preloadSecondTime);
        // 是否在运行时创建Cache
        Boolean dynamic = (Boolean) ReflectionUtils.getFieldValue(getInstance(), CacheConstants.SUPER_FIELD_DYNAMIC);
        // 是否允许存放NULL

        Boolean cacheNullValues = (Boolean) ReflectionUtils.getFieldValue3(redisCacheConfiguration, CacheConstants.SUPER_FIELD_CACHENULLVALUES);
        return dynamic ? new CustomizedRedisCache(cacheKey, (this.isUsePrefix() ? this.getCachePrefix().toString().getBytes() : null),
                this.getCacheWriter(),redisCacheConfiguration, expirationSecondTime, preloadSecondTime, cacheNullValues, cacheSupport,redisOperations,null) : null;
    }

    private RedisOperations getRedisOperations() {
        return (RedisOperations) ReflectionUtils.getFieldValue3(this, CacheConstants.SUPER_FIELD_REDISOPERATIONS);
    }

    private CacheKeyPrefix getCachePrefix() {
        return (CacheKeyPrefix) ReflectionUtils.getFieldValue(redisCacheConfiguration, CacheConstants.SUPER_FIELD_CACHEPREFIX);
    }

    private boolean isUsePrefix() {
        return (Boolean) ReflectionUtils.getFieldValue(redisCacheConfiguration, CacheConstants.SUPER_FIELD_USEPREFIX);
    }

    /**
     * 将cacheTimes中不存在的Key，但是map中存在的Key的CacheTime对象加入到cacheTimes中
     * @param map cacheSupport.getCacheTimes()方法获取到的map
     */
    private void putMapToCacheTimes(Map<String, CacheTime> map){
        if (!CollectionUtils.isEmpty(map)){
            if (cacheTimes != null){
                //遍历map
                for(Map.Entry<String, CacheTime> entry : map.entrySet()){
                    //cacheTimes不存在map中的key
                    if(!cacheTimes.containsKey(entry.getKey())){
                        cacheTimes.put(entry.getKey(), entry.getValue());
                    }
                }
            }else{
                setCacheTimes(map);
            }
        }
    }

    /**
     * 根据缓存名称设置缓存的有效时间和刷新时间，单位秒
     *
     * @param cacheTimes 缓存的名称和时间集合
     */
    public void setCacheTimes(Map<String, CacheTime> cacheTimes) {
        log.debug("setCacheTimes===>>>" + JSONObject.toJSONString(cacheTimes));
        this.cacheTimes = (cacheTimes != null ? new ConcurrentHashMap<String, CacheTime>(cacheTimes) : null);
    }

    /**
     * 设置默认的过期时间， 单位：秒
     * @param defaultExpireTime 默认过期时间
     */
//    @Override
//    public void setDefaultExpiration(long defaultExpireTime) {
////        super.setDefaultExpiration(defaultExpireTime);
//        this.defaultExpiration = defaultExpireTime;
//    }

    /**
     * 设置缓存的Key和对应的过期时间
     * @param expires 缓存的key和对应的过期时间组成的Map
     */
//    @Override
//    public void setExpires(Map<String, Long> expires) {
//        super.setExpires(expires);
//    }
}
