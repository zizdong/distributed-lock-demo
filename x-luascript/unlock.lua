-- Redis 实现分布式锁，解锁脚本
local val = redis.call('get', KEYS[1])

if not(val) then
    return -1
else
    if val == ARGV[1] then
        return redis.call('del', KEYS[1])
    else
        return -2;
    end
end