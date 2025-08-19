package xyz.zizdong.provider.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.SetParams;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.UUID;

@Slf4j
@Component
public class DistributedLock4Redis {

    @Resource
    private JedisPool jedisPool;

    protected Thread ownerThread = null;

    protected final static ThreadLocal<String> THREAD_LOCAL_THREAD = new ThreadLocal<>();

    private final static String RELEASE_LOCK_LUA_SCRIPT = "local value = redis.call('get', KEYS[1])\n" +
            "\n" +
            "if not(redis.call('get', KEYS[1])) then\n" +
            "    return -1\n" +
            "else\n" +
            "    if value == ARGV[1] then \n" +
            "        return redis.call('del', KEYS[1])\n" +
            "    else \n" +
            "        return -2\n" +
            "    end \n" +
            "end\n";

    private Jedis getJedis() {
        return jedisPool.getResource();
    }

    public boolean tryLock(String lockKey, long releaseTime, long waitTime) {
        try (Jedis jedis = getJedis()) {
            Thread thread = Thread.currentThread();
            String threadId = UUID.randomUUID().toString().replaceAll("-", "");
            thread.setName(threadId);

            long startMillis = System.currentTimeMillis();
            while (true) {
                if (System.currentTimeMillis() - startMillis < waitTime) {
                    SetParams params = new SetParams();
                    params.ex(releaseTime);
                    params.nx();
                    if (thread == ownerThread) {
                        return true;
                    } else if (ownerThread != null) {
                        continue;
                    }
                    synchronized (this) {
                        if (ownerThread == null && "OK".equals(jedis.set(lockKey, threadId, params))) {
                            log.info("线程:{}, 加锁成功", threadId);
                            THREAD_LOCAL_THREAD.set(threadId);
                            ownerThread = thread;
                            return true;
                        } else {
                            log.error("线程:{}, 加锁失败", threadId);
                            Thread.sleep(300);
                        }
                    }
                } else {
                    return false;
                }
            }
        } catch (Exception e) {
            log.error("加锁异常:", e);
            return false;
        }
    }

    public boolean unlock(String lockKey) {
        try (Jedis jedis = getJedis()) {
            String threadId = THREAD_LOCAL_THREAD.get();
            log.debug("Current thread id:{}", threadId);
            Object eval = jedis.eval(RELEASE_LOCK_LUA_SCRIPT, Arrays.asList(lockKey),
                    Arrays.asList(String.valueOf(threadId)));
            log.info("分布式锁:{}, 解锁结果:{}", lockKey, eval.toString());
            if (-1 == (long) eval) {
                log.error("分布式锁:{}, 不存在", lockKey);
                return false;
            } else if (-2 == (long) eval) {
                log.error("分布式锁:{}, 不被当前线程所持有，解锁失败", lockKey);
                log.info("线程:{}, 已加锁", threadId);
                return false;
            } else {
                if ((long) eval == 1) {
                    ownerThread = null;
                    return true;
                } else {
                    log.error("分布式锁:{}, 由于其他原因，解锁失败", lockKey);
                    return false;
                }
            }


        } catch (Exception e) {
            log.error("解锁异常", e);
            return false;
        }
    }

}
