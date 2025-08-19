package xyz.zizdong.provider.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

@Configuration
public class JedisConfig {

    @Value("${spring.redis.host}")
    private String host;

    @Value("${spring.redis.port}")
    private int port;

    @Value("${spring.redis.password}")
    private String password;

    @Value("${jedis.timeout}")
    private int timeout;

    @Value("${jedis.max-idle}")
    private int maxIdle;

    @Value("${jedis.max-wait-millis}")
    private int maxWaitMillis;

    @Value("${jedis.block-when-exhausted}")
    private boolean blockWhenExhausted;

    @Value("${jedis.jmx-enabled}")
    private boolean jmxEnabled;

    @Bean
    public JedisPool jedisPoolConfig() {
        JedisPoolConfig jedisPoolConfig = new JedisPoolConfig();
        jedisPoolConfig.setMaxIdle(maxIdle);
        jedisPoolConfig.setMaxWait(Duration.of(maxWaitMillis, ChronoUnit.MILLIS));
        jedisPoolConfig.setBlockWhenExhausted(blockWhenExhausted);
        jedisPoolConfig.setJmxEnabled(jmxEnabled);
        return new JedisPool(jedisPoolConfig, host, port, timeout, password);
    }

}
