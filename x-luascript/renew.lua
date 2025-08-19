-- Redis 分布式锁 看门狗续期脚本
if redis.call('get', KEYS[1]) == ARGV[1] then
    return redis.call('pexpire', KEYS[1], ARGV[2]);
else
    return -1;
end