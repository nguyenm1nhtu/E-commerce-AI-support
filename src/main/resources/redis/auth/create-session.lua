-- KEYS: token hash index, session family. ARGV: family UUID, user UUID, hash, TTL milliseconds.
if redis.call('EXISTS', KEYS[1]) == 1 or redis.call('EXISTS', KEYS[2]) == 1 then
    return 0
end
redis.call('HSET', KEYS[2], 'userId', ARGV[2], 'currentHash', ARGV[3])
redis.call('PEXPIRE', KEYS[2], ARGV[4])
redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[4])
return 1
