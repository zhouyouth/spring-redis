package org.twocache.twocachedemo.cache;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.support.NullValue;
import org.springframework.cache.support.SimpleValueWrapper;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.twocache.twocachedemo.config.CacheRedisConfig;
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

    private final RedisTemplate redisTemplate;
    private CacheSupport cacheSupport;

    private final RedisOperations redisOperations;
    private RedisCacheConfiguration redisCacheConfiguration;
    @Resource
    private CustomizedRedisCacheManager customizedRedisCacheManager;


    @Resource
    CacheRedisConfig cacheRedisConfig;
    private final byte[] prefix;

    /**
     * 缓存主动在失效前强制刷新缓存的时间
     * 单位：秒
     */
    private long preloadSecondTime = 0;

    /**
     * 缓存有效时间
     */
    private long expirationSecondTime;


//    public CustomizedRedisCache(String name, byte[] prefix, RedisOperations<? extends Object, ? extends Object> redisOperations, long expiration, long preloadSecondTime, CacheSupport cacheSupport) {
//        super(name, prefix, redisOperations, expiration);
//        this.redisOperations = redisOperations;
//        // 指定有效时间
//        this.expirationSecondTime = expiration;
//        // 指定自动刷新时间
//        this.preloadSecondTime = preloadSecondTime;
//        this.prefix = prefix;
//        this.cacheSupport = cacheSupport;
//    }
    @Override
   public RedisCacheConfiguration getCacheConfiguration() {
      return  this.redisCacheConfiguration = CacheRedisConfig.redisCacheConfiguration().entryTtl(Duration.ofSeconds(expirationSecondTime));
   }

    public CustomizedRedisCache(String name, byte[] prefix, RedisCacheWriter redisCacheWriter, RedisCacheConfiguration redisCacheConfiguration, long expiration, long preloadSecondTime, boolean allowNullValues, CacheSupport cacheSupport, RedisOperations redisOperations,RedisTemplate redisTemplate) {

                                     super(name, redisCacheWriter,redisCacheConfiguration

    );
                        //.serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(redisOperations.getKeySerializer()))
                        //.serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(redisOperations.getValueSerializer()));
//                        .disableCachingNullValues());
//        super(name, prefix, redisOperations, expiration, allowNullValues);
        //super(name, redisCacheWriter, redisCacheConfiguration);
       //this.redisOperations = redisOperations;
        // 指定有效时间
        this.expirationSecondTime = expiration;
        // 指定自动刷新时间
        this.preloadSecondTime = preloadSecondTime;
        this.prefix = prefix;
        this.cacheSupport = cacheSupport;
        this.redisOperations = redisOperations;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 重写get方法，获取到缓存后再次取缓存剩余的时间，如果时间小余我们配置的刷新时间就手动刷新缓存。
     * 为了不影响get的性能，启用后台线程去完成缓存的刷。
     * 并且只放一个线程去刷新数据。
     *
     * @param key  缓存的Key
     * @return ValueWrapper对象
     */
    @Override
    public ValueWrapper get(final Object key) {
        String keyString = String.valueOf(key);
        String cacheKey3 = getCacheKey(keyString);
        String invocationCacheKey = CacheSupportUtils.getInvocationCacheKey(cacheKey3);
   //      调用重写后的get方法
       // ValueWrapper valueWrapper = this.get(invocationCacheKey);
        Object lookup = lookup(key);
        if (lookup == null)
            return null;
        log.debug("执行刷新缓存的方法...");
        // 刷新缓存数据
         refreshCache(cacheKey3, invocationCacheKey,lookup);
        return toValueWrapper(lookup); // Changed from valueWrapper to toValueWrapper
    }

    //重写父类,阻止父类写入缓存（不按照自定义建，和过期时间写入）//明白为啥保存调用invacation_key了
//    @Override
//    public void put(Object key, @Nullable Object value) {
//        if (value != null) {
//            String keyString = String.valueOf(key);
//            String cacheKey3 = getCacheKey(keyString);
//            String invocationCacheKey = CacheSupportUtils.getInvocationCacheKey(cacheKey3);
//            // 刷新缓存数据
//           // refreshCache(key, invocationCacheKey, value);
//        }
//       // super.put(key, value);
//    }
//    // protected @Nullable Object lookup(Object key) {
////        RedisTemplate<String, Object> objectObjectRedisTemplate = new RedisTemplate<>();
////        objectObjectRedisTemplate.setConnectionFactory(lettuceConnectionFactory);
////        objectObjectRedisTemplate.setKeySerializer(new StringRedisSerializer());
////        objectObjectRedisTemplate.setValueSerializer(RedisSerializer.json());
////        objectObjectRedisTemplate.afterPropertiesSet();
//     //   Boolean exists =redisTemplate.hasKey((String) key);
//
////        if (Boolean.TRUE.equals(exists)) {
////            // key exists in cache, so return its value
////            // remember, the key could have an associated expiry and it could expire just after this line,
////            // so a production-level application need to handle this kind of situations properly
////            return redisTemplate.opsForValue().get(key);
////        } else {
////            // key doesn't exist in cache, so return null.
////            return null;
////        }
//
//  //  }
//    @Override
//    protected Duration getTimeToLive() {
//        return null;
//    }
    @Override
    protected ValueWrapper toValueWrapper(Object value) {
        return (value != null ? new SimpleValueWrapper(value) : (ValueWrapper) NullValue.INSTANCE);
    }

    /**
     * 重写父类的get函数。
     * 父类的get方法，是先使用exists判断key是否存在，不存在返回null，存在再到redis缓存中去取值。这样会导致并发问题，
     * 假如有一个请求调用了exists函数判断key存在，但是在下一时刻这个缓存过期了，或者被删掉了。
     * 这时候再去缓存中获取值的时候返回的就是null了。
     * 可以先获取缓存的值，再去判断key是否存在。
     *
     * @param cacheKey 缓存的key
     * @return RedisCacheElement对象
     */
//    @Override
//    public RedisCacheElement get(final RedisCacheKey cacheKey) {
//
//        Assert.notNull(cacheKey, "CacheKey must not be null!");
//
//        RedisCacheElement redisCacheElement = null;
//
//        try{
//            // 根据key获取缓存值
//            redisCacheElement = new RedisCacheElement(cacheKey, fromStoreValue(lookup(cacheKey)));
//            // 判断key是否存在
//            Boolean exists = (Boolean) redisOperations.execute(new RedisCallback<Boolean>() {
//
//                @Override
//                public Boolean doInRedis(RedisConnection connection) throws DataAccessException {
//                    return connection.exists(cacheKey.getKeyBytes());
//                }
//            });
//
//            if (!exists.booleanValue()) {
//                return null;
//            }
//        }catch (Exception e){
//            //捕获异常只打印日志，不处理
//            log.error("从缓存中获取RedisCacheElement抛出异常===>>>" + e.getMessage());
//            redisCacheElement = null;
//        }
//
//        return redisCacheElement;
//    }

    /**
     * 刷新缓存数据
     */
    private void refreshCache(Object key, final String cacheKeyStr, @Nullable Object argument) {
        Long ttl = redisOperations.getExpire(cacheKeyStr);
        System.out.println("过期时间为==" + ttl);//
        System.out.println("刷新时间===" + this.preloadSecondTime);
      //  CustomizedRedisCache.this.cacheSupport.refreshCacheByKey(CustomizedRedisCache.super.getName(), key.toString());
        log.debug("未获取到分布式锁：cacheKeyStr···············===>>>" +cacheKeyStr + ", ttl===>>>" + ttl + ",preloadSecondTime===>>>"+ this.preloadSecondTime);
        if (ttl >0 && ttl <= CustomizedRedisCache.this.preloadSecondTime) {
            // 尽量少的去开启线程，因为线程池是有限的
            ThreadTaskUtils.run(new Runnable() {
                @Override
                public void run() {
                    log.debug("进入ThreadTaskUtils.run 开始执行缓存数据刷新...");
                    // 加一个分布式锁，只放一个请求去刷新缓存
                    RedisLock redisLock = new RedisLock((RedisTemplate) redisOperations, cacheKeyStr + "_lock");
                    try {
                        if (redisLock.lock()) {
                            log.debug("获取到分布式锁：cacheKeyStr===>>>" +cacheKeyStr + ", ttl===>>>" + ttl + ",preloadSecondTime===>>>" + CustomizedRedisCache.this.preloadSecondTime);
                            // 获取锁之后再判断一下过期时间，看是否需要加载数据
//                            Long ttl = CustomizedRedisCache.this.redisOperations.getExpire(cacheKeyStr);
                            Long ttl = redisOperations.getExpire(cacheKeyStr);
                            if (null != ttl && ttl <= CustomizedRedisCache.this.preloadSecondTime) {
                                // 通过获取代理方法信息重新加载缓存数据
                                CustomizedRedisCache.this.cacheSupport.refreshCacheByKey(CustomizedRedisCache.super.getName(), key.toString());
                            }
                        }
                    } catch (Exception e) {
                        log.error(e.getMessage(), e);
                    } finally {
                        redisLock.unlock();
                    }
                }
            });
        }
    }

    public long getExpirationSecondTime() {
        return expirationSecondTime;
    }


    @Override
    public String createCacheKey(Object key) {
     //   String convertedKey;
//        if (key instanceof CachedMethodInvocation) {
//            convertedKey = key.toString();
//        } else {
//            convertedKey = this.convertKey(key);
//        }

//        if (this.getCacheConfiguration().usePrefix()) {
            return super.createCacheKey(this.convertKey(key));
//        } else {
//            return convertedKey;
//        }

    }
    /**
     * 获取RedisCacheKey
     *
     * @param key 缓存的Key
     * @return RedisCacheKey对象
     */
    public String getRedisCacheKey(Object key) {

        String convertedKey = this.convertKey(key);
        return  this.createCacheKey(convertedKey);
//        return this.getCacheConfiguration().usePrefix() ? this.createCacheKey(convertedKey) : convertedKey;
    }
//
//    /**
//     * 获取RedisCacheKey
//     * @param key 缓存的Key
//     * @return 缓存的value
//     */
    public String getCacheKey(Object key) {
        String convertedKey = this.convertKey(key);
        return this.getCacheConfiguration().usePrefix() ? this.createCacheKey(convertedKey) : convertedKey;
    }
}
