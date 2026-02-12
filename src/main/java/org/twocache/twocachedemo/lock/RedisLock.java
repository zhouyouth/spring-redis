package org.twocache.twocachedemo.lock;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @author binghe
 * @version 1.0.0
 * @description Redis分布式锁
 */
@Slf4j
public class RedisLock {

    private final RedisTemplate<Object, Object> redisTemplate;

    /**
     * 默认请求锁的超时时间(ms 毫秒)
     */
    private static final long TIME_OUT = 100;

    /**
     * 默认锁的有效时间(s)
     */
    public static final int EXPIRE = 60;

    /**
     * 解锁的lua脚本
     */
    public static final String UNLOCK_LUA =
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";

    /**
     * 锁标志对应的key
     */
    private String lockKey;

    /**
     * 记录到日志的锁标志对应的key
     */
    private String lockKeyLog = "";

    /**
     * 锁对应的值
     */
    private String lockValue;

    /**
     * 锁的有效时间(s)
     */
    private int expireTime = EXPIRE;

    /**
     * 请求锁的超时时间(ms)
     */
    private long timeOut = TIME_OUT;

    /**
     * 锁标记
     */
    private volatile boolean locked = false;

    final Random random = new Random();

    @SuppressWarnings("unchecked")
    public RedisLock(RedisTemplate redisTemplate, String lockKey) {
        this.redisTemplate = (RedisTemplate<Object, Object>) redisTemplate;
        this.lockKey = lockKey + "_lock";
    }

    public RedisLock(RedisTemplate redisTemplate, String lockKey, int expireTime) {
        this(redisTemplate, lockKey);
        this.expireTime = expireTime;
    }

    public RedisLock(RedisTemplate redisTemplate, String lockKey, long timeOut) {
        this(redisTemplate, lockKey);
        this.timeOut = timeOut;
    }

    public RedisLock(RedisTemplate redisTemplate, String lockKey, int expireTime, long timeOut) {
        this(redisTemplate, lockKey, expireTime);
        this.timeOut = timeOut;
    }

    /**
     * 尝试获取锁 超时返回
     *
     * @return 获取锁成功返回true,失败返回false
     */
    public boolean tryLock() {
        // 生成随机key
        lockValue = UUID.randomUUID().toString();
        // 请求锁超时时间，纳秒
        long timeoutNanos = timeOut * 1_000_000;
        // 系统当前时间，纳秒
        long startTime = System.nanoTime();
        while ((System.nanoTime() - startTime) < timeoutNanos) {
            if (acquireLock()) {
                return true;
            }
            // 每次请求等待一段时间
            sleep(10, 50000);
        }
        return locked;
    }

    /**
     * 尝试获取锁 立即返回
     *
     * @return 是否成功获得锁
     */
    public boolean lock() {
        lockValue = UUID.randomUUID().toString();
        return acquireLock();
    }

    /**
     * 以阻塞方式的获取锁
     *
     * @return 是否成功获得锁
     */
    public boolean lockBlock() {
        lockValue = UUID.randomUUID().toString();
        while (true) {
            if (acquireLock()) {
                return true;
            }
            // 每次请求等待一段时间
            sleep(10, 50000);
        }
    }

    private boolean acquireLock() {
        Boolean result = redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, expireTime, TimeUnit.SECONDS);
        locked = Boolean.TRUE.equals(result);
        if (locked && StringUtils.hasText(lockKeyLog)) {
            log.info("获取锁{}的时间：{}", lockKeyLog, System.currentTimeMillis());
        }
        return locked;
    }

    /**
     * 解锁
     *
     * @return 释放锁结果，true:释放成功； false:释放失败
     */
    public Boolean unlock() {
        if (!locked) {
            return true;
        }

        RedisScript<Long> script = new DefaultRedisScript<>(UNLOCK_LUA, Long.class);
        Long result = redisTemplate.execute(script, Collections.singletonList(lockKey), lockValue);

        if (Long.valueOf(1L).equals(result)) {
            locked = false;
            return true;
        }
        
        if (StringUtils.hasText(lockKeyLog)) {
            log.info("Redis分布式锁，解锁{}失败！解锁时间：{}", lockKeyLog, System.currentTimeMillis());
        }
        return false;
    }

    private void sleep(long millis, int nanos) {
        try {
            Thread.sleep(millis, random.nextInt(nanos));
        } catch (InterruptedException e) {
            log.info("获取分布式锁休眠被中断：", e);
            Thread.currentThread().interrupt();
        }
    }

    public String getLockKeyLog() {
        return lockKeyLog;
    }

    public void setLockKeyLog(String lockKeyLog) {
        this.lockKeyLog = lockKeyLog;
    }

    public int getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(int expireTime) {
        this.expireTime = expireTime;
    }

    public long getTimeOut() {
        return timeOut;
    }

    public void setTimeOut(long timeOut) {
        this.timeOut = timeOut;
    }
}
