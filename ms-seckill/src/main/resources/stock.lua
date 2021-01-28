-- 先判断 key 是否存在
if (redis.call('hexists', KEYS[1], KEYS[2]) == 1) then
-- 获取库存
	local stock = tonumber(redis.call('hget', KEYS[1], KEYS[2]));
	if (stock > 0) then
-- 	库存减一
	   redis.call('hincrby', KEYS[1], KEYS[2], -1);
	   return stock;
	end;
    return 0;
end;