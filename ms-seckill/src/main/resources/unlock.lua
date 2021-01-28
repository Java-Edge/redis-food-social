-- 锁的 key
local key = KEYS[1];
-- 线程唯一标识
local threadId = ARGV[1];

-- 判断当前锁是否还是被自己持有
if (redis.call('hexists', key, threadId) == 0) then
--     如果已经不是自己，则直接返回
    return nil;
end;
-- 是自己的锁，则重入次数减一
local count = redis.call('hincrby', key, threadId, -1);

-- 判断重入次数是否已为0
if (count == 0) then
--     等于 0，说明可以释放锁，直接删除
    redis.call('del', key);
    return nil;
end;