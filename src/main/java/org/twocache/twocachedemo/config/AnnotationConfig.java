package org.twocache.twocachedemo.config;


import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.PropertySource;

/**
 * @author binghe
 * @version 1.0.0
 * @description 提供以Java注解的形式配置Spring和Redis集群整合的Spring容器管理
 */
@Configuration
@EnableCaching
@EnableAspectJAutoProxy(proxyTargetClass = true)
@ComponentScan(value = {"org.twocache.twocachedemo"})
@PropertySource(value = {"classpath:properties/redis-default.properties", "classpath:application.properties"})
public class AnnotationConfig extends CacheRedisConfig {
}