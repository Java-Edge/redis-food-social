local key = KEYS[1];
-- 线程唯一标识
local threadId = ARGV[1];
-- 锁的自动释放时间
local releaseTime = ARGV[2];

-- 判断是否已存在
if(redis.call('exists', key) == 0) then
--     不存在，则获取锁
    redis.call('hset', key, threadId, '1');
--     设置有效期
    redis.call('expire', key, releaseTime);
--     返回结果
    return 1;
end;

-- 锁已存在
if(redis.call('hexists', key, threadId) == 1) then
--     如果是自己，则重入次数加一
    redis.call('hincrby', key, threadId, '1');
--     设置有效期
    redis.call('expire', key, releaseTime);
--     返回结果
    return 1;
end;
-- 走到这里，说明获取锁的不是自己，获取锁失败
return 0;