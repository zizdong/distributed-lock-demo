package xyz.zizdong.provider.utils;

import com.sun.org.apache.bcel.internal.generic.RETURN;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.SetParams;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class DistributedLock4RedisWithWatchDog extends DistributedLock4Redis {

    @Resource
    private JedisPool jedisPool;

    private final static DelayQueue<DelayedItem> DELAY_QUEUE = new DelayQueue<>();

    private Thread watchdogThread = null;

    private final static long DEFAULT_RENEW_TIME = 2000;
    private final static long DEFAULT_RELEASE_TIME = 3;

    private final static String RENEW_LUA_SCRIPT = "if redis.call('get', KEYS[1]) == ARGV[1] then\n" +
            "    return redis.call('pexpire', KEYS[1], ARGV[2])\n" +
            "else\n" +
            "    return 0\n" +
            "end";

    private Jedis getJedis() {
        return jedisPool.getResource();
    }

    public boolean tryLock(String lockKey, long waitTime) {
        try (Jedis jedis = getJedis()) {
            Thread thread = Thread.currentThread();
            String threadId = UUID.randomUUID().toString().replaceAll("-", "");
            thread.setName(threadId);

            long startMillis = System.currentTimeMillis();
            while (true) {
                if (System.currentTimeMillis() - startMillis < waitTime) {
                    SetParams params = new SetParams();
                    params.ex(DEFAULT_RELEASE_TIME);
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
                            // 加锁成功后需要开启看门狗线程
                            if (watchdogThread == null) {
                                watchdogThread = new Thread(new WatchDogRunnable());
                                watchdogThread.setDaemon(true);
                                watchdogThread.start();
                            }
                            DELAY_QUEUE.add(new DelayedItem(DEFAULT_RELEASE_TIME * 1000, threadId, lockKey));
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

    private class WatchDogRunnable implements Runnable {
        @Override
        public void run() {
            log.info("看门狗线程已启动....");
            while (!Thread.currentThread().isInterrupted()) {
                try (Jedis jedis = getJedis()) {
                    log.info("获取jedis对象成功");
                    DelayedItem take = DELAY_QUEUE.take();
                    log.info("从延迟队列获取数据成功:{}", take.getDelay(TimeUnit.MILLISECONDS));
                    Long eval = (Long) jedis.eval(RENEW_LUA_SCRIPT, Collections.singletonList(take.getLockKey()),
                            Arrays.asList(take.getThreadId(), String.valueOf(DEFAULT_RENEW_TIME)));
                    if (eval.longValue() == 0) {
                        log.info("锁已释放，无需续期！");
                    } else {
                        log.info("锁已续期，续期时间2s");
                        DELAY_QUEUE.add(new DelayedItem(DEFAULT_RENEW_TIME, take.getThreadId(), take.getLockKey()));
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            log.info("看门狗线程已结束.....");
        }
    }

    @Data
    @NoArgsConstructor
    public static class DelayedItem implements Delayed {

        private long expiredTime;
        private String threadId;
        private String lockKey;

        public DelayedItem(long expiredTime, String threadId, String lockKey) {
            this.expiredTime = expiredTime + System.currentTimeMillis() - 100;
            this.threadId = threadId;
            this.lockKey = lockKey;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(expiredTime - System.currentTimeMillis() , TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(Delayed otherDelayed) {
            DelayedItem other = (DelayedItem) otherDelayed;
            if (this.expiredTime < other.expiredTime) {
                return -1;
            } else if (this.expiredTime > other.expiredTime) {
                return 1;
            }
            return 0;
        }
    }
}
