package org.twocache.twocachedemo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.twocache.twocachedemo.service.RedisService;
import org.twocache.twocachedemo.utils.SpringContext;
import org.twocache.twocachedemo.utils.ThreadTaskUtils;

import java.util.concurrent.atomic.AtomicBoolean;

@SpringBootApplication
@Slf4j
public class TwocachedemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(TwocachedemoApplication.class, args);

        // 定义一个标志位用于控制循环退出
        AtomicBoolean isRunning = new AtomicBoolean(true);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("接收到关闭信号，准备停止任务...");
            isRunning.set(false); // 设置标志位为 false，通知循环退出
        }));

        ThreadTaskUtils.run(() -> {
            // 使用 ApplicationContext 获取 RedisService 实例，避免静态注入问题
            RedisService redisService = SpringContext.getBean(RedisService.class);
            if (redisService == null) {
                log.error("RedisService 未正确初始化，请检查配置！");
                return;
            }

            int logCounter = 0; // 日志计数器，用于控制日志频率
            while (isRunning.get()) { // 使用标志位控制循环退出
                try {
                    String result = redisService.getRedidInfo("redis_test", "default_value1");
                    logCounter++;
                    if (logCounter % 10 == 0) { // 每10次执行打印一次日志
                        log.info("Redis 查询结果: {}", result);
                    }
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    log.warn("线程被中断，即将退出循环");
                    Thread.currentThread().interrupt(); // 恢复中断状态
                    break; // 直接退出循环
                } catch (Exception e) {
                    log.error("执行 Redis 操作时发生异常", e);
                }
            }

            log.info("任务已安全退出");
        });
    }


}
