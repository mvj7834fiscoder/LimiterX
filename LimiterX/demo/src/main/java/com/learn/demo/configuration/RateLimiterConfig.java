package com.learn.demo.configuration;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.time.Duration;

@Configuration
public class RateLimiterConfig {

    private RedisClient redisClient;
    private StatefulRedisConnection<String, byte[]> connection;

    @Bean
    public RedisClient redisClient() {
        this.redisClient = RedisClient.create(RedisURI.builder()
                .withHost("localhost")
                .withPort(6379)
                .build());
        return this.redisClient;
    }

    @Bean
    @Lazy
    public ProxyManager<String> lettuceBasedProxyManager(RedisClient redisClient) {
        this.connection = redisClient
                .connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));

        return LettuceBasedProxyManager.builderFor(connection)
                .withExpirationStrategy(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(10)))
                .build();
    }

    // Hook into Spring Lifecycle to close sockets on application shutdown
    @PreDestroy
    public void cleanUpConnections() {
        if (connection != null) {
            connection.close();
        }
        if (redisClient != null) {
            redisClient.shutdown();
        }
    }
}
