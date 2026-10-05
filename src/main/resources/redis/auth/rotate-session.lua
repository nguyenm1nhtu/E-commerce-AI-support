-- KEYS: old hash index, new hash index, family. ARGV: family UUID, old hash, new hash.
if redis.call('GET', KEYS[1]) ~= ARGV[1] then
    return ''
end
local ttl = redis.call('PTTL', KEYS[3])
if ttl < 1000 or redis.call('PTTL', KEYS[1]) < 1000 then
    return ''
end
if redis.call('HGET', KEYS[3], 'currentHash') ~= ARGV[2] then
    -- Keep spent hash indexes until the absolute session expiry to detect replay.
    redis.call('DEL', KEYS[3])
    return ''
end
local userId = redis.call('HGET', KEYS[3], 'userId')
if not userId then
    return ''
end
if not redis.call('SET', KEYS[2], ARGV[1], 'PX', ttl, 'NX') then
    return redis.error_reply('Refresh token collision')
end
redis.call('HSET', KEYS[3], 'currentHash', ARGV[3])
return userId .. '|' .. ttl
