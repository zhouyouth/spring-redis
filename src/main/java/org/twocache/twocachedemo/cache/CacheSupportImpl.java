package org.twocache.twocachedemo.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.expression.AnnotatedElementKey;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.expression.EvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MethodInvoker;
import org.springframework.util.StringUtils;
import org.twocache.twocachedemo.cache.expression.CacheOperationExpressionEvaluator;
import org.twocache.twocachedemo.constants.CacheConstants;
import org.twocache.twocachedemo.utils.ReflectionUtils;
import org.twocache.twocachedemo.utils.SpringContext;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * @author binghe
 * @version 1.0.0
 * @description 手动刷新缓存实现类
 */
@Slf4j
@Component
public class CacheSupportImpl implements CacheSupport {

    private final CacheOperationExpressionEvaluator evaluator = new CacheOperationExpressionEvaluator();

    @Autowired
    private KeyGenerator keyGenerator;

    @Autowired
    private RedisCacheManager cacheManager;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public void registerInvocation(Object targetBean, Method targetMethod, Class[] invocationParamTypes,
                                   Object[] invocationArgs, Set<String> annotatedCacheNames, String cacheKey) {
        try {
            Collection<? extends Cache> caches = getCache(annotatedCacheNames);
            Object key = generateKey(caches, cacheKey, targetMethod, invocationArgs, targetBean, CacheOperationExpressionEvaluator.NO_RESULT);

            final CachedMethodInvocation invocation = new CachedMethodInvocation(key, targetBean, targetMethod, invocationParamTypes, invocationArgs);
            for (Cache cache : caches) {
                if (cache instanceof CustomizedRedisCache) {
                    CustomizedRedisCache redisCache = ((CustomizedRedisCache) cache);
                    String keyString = String.valueOf(key);
                    String cacheKeyStr = redisCache.createCacheKey(keyString);
                    String invocationCacheKey = CacheSupportUtils.getInvocationCacheKey(cacheKeyStr);
                    
                    // 存储方法调用信息，过期时间与缓存一致
                    redisTemplate.opsForValue().set(invocationCacheKey, invocation, redisCache.getExpirationSecondTime(), TimeUnit.SECONDS);
                }
            }
        } catch (Exception e) {
            log.error("注册缓存方法信息抛出异常: {}", e.getMessage());
        }
    }

    @Override
    public void refreshCacheByKey(String cacheName, String cacheKey) {
        String invocationCacheKey = CacheSupportUtils.getInvocationCacheKey(cacheKey);
        Object result = redisTemplate.opsForValue().get(invocationCacheKey);

        if (result instanceof CachedMethodInvocation) {
            CachedMethodInvocation invocation = (CachedMethodInvocation) result;
            refreshCache(invocation, cacheName);
        }
    }

    private void refreshCache(CachedMethodInvocation invocation, String cacheName) {
        try {
            Object computed = invoke(invocation);
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.put(invocation.getKey(), computed);
                
                // 同时也需要更新 invocation 的过期时间
                if (cache instanceof CustomizedRedisCache) {
                    CustomizedRedisCache redisCache = (CustomizedRedisCache) cache;
                    String cacheKeyStr = redisCache.createCacheKey(invocation.getKey());
                    String invocationCacheKey = CacheSupportUtils.getInvocationCacheKey(cacheKeyStr);
                    redisTemplate.expire(invocationCacheKey, redisCache.getExpirationSecondTime(), TimeUnit.SECONDS);
                }
                
                log.info("刷新缓存成功: {}-{}", cacheName, invocation.getKey());
            }
        } catch (Exception e) {
            log.error("刷新缓存失败: {}", e.getMessage(), e);
        }
    }

    private Object invoke(CachedMethodInvocation invocation) throws Exception {
        Object[] args = null;
        if (!CollectionUtils.isEmpty(invocation.getArguments())) {
            args = invocation.getArguments().toArray();
        }
        
        Class<?> clazz = ReflectionUtils.getInterfaceClass(invocation.getTargetBean(), invocation.getTargetMethod(), invocation.getParameterTypes());
        
        // 使用 SpringContext 直接获取 Bean，避免使用脆弱的 contextKey
        Object bean = SpringContext.getBean(clazz);
        
        Object target = clazz.isInterface() ? bean : ReflectionUtils.getTarget(bean);
        
        final MethodInvoker invoker = new MethodInvoker();
        invoker.setTargetObject(target);
        invoker.setArguments(args);
        invoker.setTargetMethod(invocation.getTargetMethod());
        invoker.prepare();

        return invoker.invoke();
    }

    @Override
    public Map<String, CacheTime> getCacheTimes(String cacheName) {
        ConcurrentMap<String, CacheTime> map = new ConcurrentHashMap<>();
        long expireTime = CacheConstants.DEFAULT_EXPIRATION_SECOND_TIME;
        long preloadTime = CacheConstants.DEFAULT_PRELOAD_SECOND_TIME;
        String cacheKey = CacheConstants.DEFAULT_EXPIRATION_KEY;

        if (StringUtils.hasText(cacheName)) {
            if (cacheName.contains(CacheConstants.SEPARATOR)) {
                String[] cacheArray = cacheName.split(CacheConstants.SEPARATOR);
                if (cacheArray.length > 0) {
                    cacheKey = cacheArray[0].trim();
                }
                if (cacheArray.length > 1) {
                    try {
                        expireTime = Long.parseLong(cacheArray[1].trim());
                    } catch (NumberFormatException e) {
                        expireTime = CacheConstants.DEFAULT_EXPIRATION_SECOND_TIME;
                    }
                }
                if (cacheArray.length > 2) {
                    try {
                        preloadTime = Long.parseLong(cacheArray[2].trim());
                    } catch (NumberFormatException e) {
                        preloadTime = CacheConstants.DEFAULT_PRELOAD_SECOND_TIME;
                    }
                }
            } else {
                cacheKey = cacheName.trim();
            }
        }
        map.put(cacheKey, new CacheTime(expireTime, preloadTime));
        return map;
    }

    @Override
    public String getCacheKey(String cacheName) {
        String cacheKey = CacheConstants.DEFAULT_EXPIRATION_KEY;
        if (StringUtils.hasText(cacheName)) {
            if (cacheName.contains(CacheConstants.SEPARATOR)) {
                String[] cacheArray = cacheName.split(CacheConstants.SEPARATOR);
                if (cacheArray.length > 0) {
                    cacheKey = cacheArray[0].trim();
                }
            } else {
                cacheKey = cacheName.trim();
            }
        }
        return cacheKey;
    }

    protected Object generateKey(Collection<? extends Cache> caches, String key, Method method, Object[] args,
                                 Object target, Object result) {
        Class<?> targetClass = getTargetClass(target);
        if (StringUtils.hasText(key)) {
            EvaluationContext evaluationContext = evaluator.createEvaluationContext(caches, method, args, target,
                    targetClass, result, null);

            AnnotatedElementKey methodCacheKey = new AnnotatedElementKey(method, targetClass);
            return evaluator.key(key, methodCacheKey, evaluationContext);
        }
        return this.keyGenerator.generate(target, method, args);
    }

    private Class<?> getTargetClass(Object target) {
        Class<?> targetClass = AopProxyUtils.ultimateTargetClass(target);
        if (targetClass == null && target != null) {
            targetClass = target.getClass();
        }
        return targetClass;
    }

    public Collection<? extends Cache> getCache(Set<String> annotatedCacheNames) {
        Collection<String> cacheNames = generateValue(annotatedCacheNames);
        if (cacheNames == null) {
            return Collections.emptyList();
        } else {
            Collection<Cache> result = new ArrayList<>();
            for (String cacheName : cacheNames) {
                Cache cache = this.cacheManager.getCache(cacheName);
                if (cache == null) {
                    throw new IllegalArgumentException("Cannot find cache named '" + cacheName + "'");
                }
                result.add(cache);
            }
            return result;
        }
    }

    private Collection<String> generateValue(Set<String> annotatedCacheNames) {
        Collection<String> cacheNames = new HashSet<>();
        for (final String cacheName : annotatedCacheNames) {
            String[] cacheParams = cacheName.split(CacheConstants.SEPARATOR);
            String realCacheName = cacheParams[0];
            cacheNames.add(realCacheName);
        }
        return cacheNames;
    }
}
