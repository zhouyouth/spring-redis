package org.twocache.twocachedemo.config;

import com.alibaba.fastjson.parser.ParserConfig;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.data.redis.autoconfigure.DataRedisProperties;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.CacheResolver;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.CacheKeyPrefix;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisNode;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.twocache.twocachedemo.aspect.CachingAnnotationsAspect;
import org.twocache.twocachedemo.cache.CacheKeyGenerator;
import org.twocache.twocachedemo.cache.CacheTime;
//import org.twocache.twocachedemo.cache.CustomizedRedisCacheManager;
import org.twocache.twocachedemo.cache.CustomizedRedisCacheManager;
import org.twocache.twocachedemo.constants.CacheConstants;
import org.twocache.twocachedemo.serializer.FastJsonRedisSerializer;
import org.twocache.twocachedemo.serializer.GenericJackson2JsonRedisSerializer;
import redis.clients.jedis.JedisPoolConfig;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.springframework.data.redis.cache.RedisCacheConfiguration.registerDefaultConverters;

/**
 * @author binghe
 * @version 1.0.0
 * @description Redis配置类
 */
@Slf4j
@Configuration
@EnableCaching
public class CacheRedisConfig extends BaseRedisConfig {

    /**
     * 配置 JedisPoolConfig
     * @return JedisPoolConfig对象
     */
    @Bean
    public JedisPoolConfig jedisPoolConfig(){
        JedisPoolConfig jedisPoolConfig = new JedisPoolConfig();
        jedisPoolConfig.setMaxTotal(maxTotal);
        jedisPoolConfig.setMaxIdle(maxIdle);
        jedisPoolConfig.setMinIdle(minIdle);
        jedisPoolConfig.setBlockWhenExhausted(blockWhenExhausted);
        jedisPoolConfig.setMaxWaitMillis(maxWaitMillis);
        jedisPoolConfig.setTestOnBorrow(testOnBorrow);
        jedisPoolConfig.setTestOnReturn(testOnReturn);
        jedisPoolConfig.setTestWhileIdle(testWhileIdle);
        jedisPoolConfig.setMinEvictableIdleTimeMillis(minEvictableIdleTimeMillis);
        jedisPoolConfig.setTimeBetweenEvictionRunsMillis(timeBetweenEvictionRunsMillis);
        jedisPoolConfig.setNumTestsPerEvictionRun(numTestsPerEvictionRun);
        return jedisPoolConfig;
    }

    /**
     * 配置 RedisClusterConfiguration
     * @return RedisClusterConfiguration对象
     */
    @Bean
    public RedisClusterConfiguration redisClusterConfiguration(){
        RedisClusterConfiguration redisClusterConfiguration = new RedisClusterConfiguration();
        redisClusterConfiguration.setMaxRedirects(3);
        redisClusterConfiguration.setClusterNodes(getRedisNodes());
        return redisClusterConfiguration;
    }

    /**
     * 配置 JedisConnectionFactory
     * @return 返回JedisConnectionFactory对象
     */
    @Bean
    public JedisConnectionFactory jedisConnectionFactory(){
        JedisConnectionFactory jedisConnectionFactory = new JedisConnectionFactory(redisClusterConfiguration(), jedisPoolConfig());
        jedisConnectionFactory.setPassword(password);
//        jedisConnectionFactory.setPassword("wuxinggou");
        jedisConnectionFactory.setTimeout(timeout);
        return jedisConnectionFactory;
    }

      @Bean
      public LettuceConnectionFactory lettuceConnectionFactory(){
          LettuceConnectionFactory lettuceConnectionFactory = new LettuceConnectionFactory(redisClusterConfiguration());
          lettuceConnectionFactory.setPassword(password);
          lettuceConnectionFactory.setTimeout(timeout);
          return lettuceConnectionFactory;
      }
    /**
     * 配置RedisTemplate
     * @return RedisTemplate对象
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(){RedisTemplate<String, Object> redisTemplate = new RedisTemplate<String, Object>();
       redisTemplate.setConnectionFactory(jedisConnectionFactory());
        StringRedisSerializer keySerializer = new StringRedisSerializer();
       // FastJsonRedisSerializer<Object> valueSerializer = new FastJsonRedisSerializer<Object>();
        GenericJackson2JsonRedisSerializer genericJackson2JsonRedisSerializer = new GenericJackson2JsonRedisSerializer();
        ParserConfig.getGlobalInstance().setAutoTypeSupport(true);
        redisTemplate.setKeySerializer(keySerializer);
        redisTemplate.setValueSerializer(genericJackson2JsonRedisSerializer);
        redisTemplate.setHashKeySerializer(keySerializer);
        redisTemplate.setHashValueSerializer(genericJackson2JsonRedisSerializer);


        // 新建一个代理对象（记录了缓存注解的方法类信息）
        RedisTemplate<String, Object> objectObjectRedisTemplate = new RedisTemplate<>();
        objectObjectRedisTemplate.setConnectionFactory(jedisConnectionFactory());
        objectObjectRedisTemplate.setKeySerializer(new StringRedisSerializer());
        // objectObjectRedisTemplate.setValueSerializer(RedisSerializer.json());
        // objectObjectRedisTemplate.setValueSerializer(new FastJsonRedisSerializer<>(Object.class));
        objectObjectRedisTemplate.setValueSerializer(RedisSerializer.json());
        objectObjectRedisTemplate.afterPropertiesSet();

        return redisTemplate;
    }
//    @Bean
//    public RedisOperations<String, Object> redisOperations(){
//        return redisTemplate();
//    }
    @Bean
    public RedisCacheWriter redisCacheWriter(){
       return RedisCacheWriter.lockingRedisCacheWriter(lettuceConnectionFactory());
//        this.lockTtlFunction = TtlFunction.persistent();
        //   return     RedisCacheWriter.lockingRedisCacheWriter(jedisConnectionFactory());

    }
//    public static RedisCacheConfiguration defaultCacheConfig(@Nullable ClassLoader classLoader) {
//        DefaultFormattingConversionService conversionService = new DefaultFormattingConversionService();
       // registerDefaultConverters(conversionService);
//        return new RedisCacheConfiguration(RedisCacheWriter.TtlFunction.persistent(), true, false, true, CacheKeyPrefix.simple(), RedisSerializationContext.SerializationPair.fromSerializer(RedisSerializer.string()), RedisSerializationContext.SerializationPair.fromSerializer(RedisSerializer.java(classLoader)), conversionService);
//    }
@Bean
public static RedisCacheConfiguration redisCacheConfiguration() {
    return RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofSeconds(CacheConstants.DEFAULT_EXPIRATION_SECOND_TIME))
           // .disableKeyPrefix()
            .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer())) //
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()));//这里控制springredis  cache.put()配置序列化方法
}
    @Bean
    public CustomizedRedisCacheManager customizedRedisCacheManager(){
        CustomizedRedisCacheManager customizedAnnotationRedisCacheManager = new CustomizedRedisCacheManager(redisCacheWriter(), redisTemplate(),redisCacheConfiguration());
//        customizedAnnotationRedisCacheManager.setDefaultExpiration(3600);
//        customizedAnnotationRedisCacheManager.setUsePrefix(usePrefix);
        Map<String, CacheTime> map = new HashMap<String, CacheTime>();
        map.put(defaultExpirationKey, cacheTime());
        customizedAnnotationRedisCacheManager.setCacheTimes(map);
        return customizedAnnotationRedisCacheManager;
    }

    /**
     * 配置CacheAnnotationTime
     * @return CacheAnnotationTime对象
     */
    @Bean
    public CacheTime cacheTime(){
        CacheTime cacheTime = new CacheTime(expirationSecondTime, preloadSecondTime);
        return cacheTime;
    }

    /**
     * 配置 CacheAnnotationKeyGenerator
     * @return CacheAnnotationKeyGenerator对象
     */
    @Bean
    public CacheKeyGenerator cacheKeyGenerator(){
        CacheKeyGenerator cacheKeyGenerator = new CacheKeyGenerator();
        return cacheKeyGenerator;
    }

    /**
     * 配置SpringCachingAnnotationsAspect
     * @return SpringCachingAnnotationsAspect对象
     */
    @Bean
    public CachingAnnotationsAspect cachingAnnotationsAspect(){
        CachingAnnotationsAspect cachingAnnotationsAspect = new CachingAnnotationsAspect();
        return cachingAnnotationsAspect;
    }



    /**
     * 封装各Redis节点信息
     * @return Redis节点Set集合
     */
    private Set<RedisNode> getRedisNodes(){
        Set<RedisNode> set = new HashSet<RedisNode>();
        RedisNode redisNode1 = new RedisNode(nodeOne, nodeOnePort);
        set.add(redisNode1);

        RedisNode redisNode2 = new RedisNode(nodeTwo, nodeTwoPort);
        set.add(redisNode2);

        RedisNode redisNode3 = new RedisNode(nodeThree, nodeThreePort);
        set.add(redisNode3);

        RedisNode redisNode4 = new RedisNode(nodeFour, nodeFourPort);
        set.add(redisNode4);

        RedisNode redisNode5 = new RedisNode(nodeFive, nodeFivePort);
        set.add(redisNode5);

        RedisNode redisNode6 = new RedisNode(nodeSix, nodeSixPort);
        set.add(redisNode6);

        RedisNode redisNode7 = new RedisNode(nodeSeven, nodeSevenPort);
        set.add(redisNode7);
        return set;
    }

//    @Override
//    public CacheManager cacheManager() {
//        return customizedRedisCacheManager();
//    }

//    @Override
//    @Bean
//    public KeyGenerator keyGenerator() {
//        return cacheKeyGenerator();
//    }

//    @Override
//    public CacheResolver cacheResolver() {
//        return super.cacheResolver();
//    }
//
//    @Override
//    public CacheErrorHandler errorHandler() {
//        CacheErrorHandler cacheErrorHandler = new CacheErrorHandler() {
//
//            @Override
//            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
//                RedisErrorException(exception, key);
//            }
//
//            @Override
//            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
//                RedisErrorException(exception, key);
//            }
//
//            @Override
//            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
//                RedisErrorException(exception, key);
//            }
//
//            @Override
//            public void handleCacheClearError(RuntimeException exception, Cache cache) {
//                RedisErrorException(exception, null);
//            }
//        };
//        return cacheErrorHandler;
//    }
//
//    protected void RedisErrorException(Exception exception,Object key){
//        log.error("redis异常：key=[{}], exception={}", key, exception.getMessage());
//    }
}
