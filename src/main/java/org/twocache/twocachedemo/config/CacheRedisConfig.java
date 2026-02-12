package org.twocache.twocachedemo.config;

import com.alibaba.fastjson.parser.ParserConfig;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.alibaba.fastjson.support.config.FastJsonConfig;
import com.alibaba.fastjson.support.spring.FastJsonRedisSerializer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisNode;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.twocache.twocachedemo.aspect.CachingAnnotationsAspect;
import org.twocache.twocachedemo.cache.CacheKeyGenerator;
import org.twocache.twocachedemo.cache.CacheSupport;
import org.twocache.twocachedemo.cache.CacheTime;
import org.twocache.twocachedemo.cache.CustomizedRedisCacheManager;
import org.twocache.twocachedemo.constants.CacheConstants;
import redis.clients.jedis.JedisPoolConfig;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author binghe
 * @version 1.0.0
 * @description Redis配置类
 */
@Slf4j
@Configuration
@EnableCaching
public class CacheRedisConfig extends BaseRedisConfig {

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

    @Bean
    public RedisClusterConfiguration redisClusterConfiguration(){
        RedisClusterConfiguration redisClusterConfiguration = new RedisClusterConfiguration();
        redisClusterConfiguration.setMaxRedirects(3);
        redisClusterConfiguration.setClusterNodes(getRedisNodes());
        return redisClusterConfiguration;
    }

    @Bean
    public JedisConnectionFactory jedisConnectionFactory(){
        JedisConnectionFactory jedisConnectionFactory = new JedisConnectionFactory(redisClusterConfiguration(), jedisPoolConfig());
        jedisConnectionFactory.setPassword(password);
        jedisConnectionFactory.setTimeout(timeout);
        return jedisConnectionFactory;
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(){
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(jedisConnectionFactory());
        
        StringRedisSerializer keySerializer = new StringRedisSerializer();
        
        // 配置 FastJsonRedisSerializer
        FastJsonRedisSerializer<Object> fastJsonRedisSerializer = new FastJsonRedisSerializer<>(Object.class);
        FastJsonConfig fastJsonConfig = new FastJsonConfig();
        fastJsonConfig.setSerializerFeatures(SerializerFeature.WriteClassName);
        fastJsonRedisSerializer.setFastJsonConfig(fastJsonConfig);
        
        ParserConfig.getGlobalInstance().setAutoTypeSupport(true);
        
        redisTemplate.setKeySerializer(keySerializer);
        redisTemplate.setValueSerializer(fastJsonRedisSerializer);
        redisTemplate.setHashKeySerializer(keySerializer);
        redisTemplate.setHashValueSerializer(fastJsonRedisSerializer);
        
        redisTemplate.afterPropertiesSet();
        return redisTemplate;
    }

    @Bean
    public RedisCacheWriter redisCacheWriter(){
        // 使用 Jedis 连接工厂以保持一致性
        return RedisCacheWriter.lockingRedisCacheWriter(jedisConnectionFactory());
    }

    @Bean
    public RedisCacheConfiguration redisCacheConfiguration() {
        // 配置 FastJsonRedisSerializer
        FastJsonRedisSerializer<Object> fastJsonRedisSerializer = new FastJsonRedisSerializer<>(Object.class);
        FastJsonConfig fastJsonConfig = new FastJsonConfig();
        fastJsonConfig.setSerializerFeatures(SerializerFeature.WriteClassName);
        fastJsonRedisSerializer.setFastJsonConfig(fastJsonConfig);

        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(CacheConstants.DEFAULT_EXPIRATION_SECOND_TIME))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(fastJsonRedisSerializer));
    }

    @Bean
    public CustomizedRedisCacheManager customizedRedisCacheManager(CacheSupport cacheSupport){
        // 注意参数顺序：Writer, Config, Operations, CacheSupport
        CustomizedRedisCacheManager customizedAnnotationRedisCacheManager = new CustomizedRedisCacheManager(redisCacheWriter(), redisCacheConfiguration(), redisTemplate(), cacheSupport);
        
        Map<String, CacheTime> map = new HashMap<>();
        map.put(defaultExpirationKey, cacheTime());
        customizedAnnotationRedisCacheManager.setCacheTimes(map);
        return customizedAnnotationRedisCacheManager;
    }

    @Bean
    public CacheTime cacheTime(){
        return new CacheTime(expirationSecondTime, preloadSecondTime);
    }

    @Bean
    public CacheKeyGenerator cacheKeyGenerator(){
        return new CacheKeyGenerator();
    }

    @Bean
    public CachingAnnotationsAspect cachingAnnotationsAspect(){
        return new CachingAnnotationsAspect();
    }

    private Set<RedisNode> getRedisNodes(){
        Set<RedisNode> set = new HashSet<>();
        set.add(new RedisNode(nodeOne, nodeOnePort));
        set.add(new RedisNode(nodeTwo, nodeTwoPort));
        set.add(new RedisNode(nodeThree, nodeThreePort));
        set.add(new RedisNode(nodeFour, nodeFourPort));
        set.add(new RedisNode(nodeFive, nodeFivePort));
        set.add(new RedisNode(nodeSix, nodeSixPort));
        set.add(new RedisNode(nodeSeven, nodeSevenPort));
        return set;
    }
}
