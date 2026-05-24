-- KEYS[1] = slot cache key (HASH)
-- ARGV[1] = new version (int)
-- ARGV[2] = claimed_by (uuid or empty)
-- ARGV[3] = claimed_at (ISO-8601 or empty)
-- ARGV[4] = slot row id (uuid)
-- Returns 1 if written, 0 if skipped (cached version is newer or equal).
local current = tonumber(redis.call('HGET', KEYS[1], 'version') or '0')
local new_version = tonumber(ARGV[1])
if new_version > current then
  redis.call('HSET', KEYS[1],
    'version', ARGV[1],
    'claimed_by', ARGV[2],
    'claimed_at', ARGV[3],
    'id', ARGV[4])
  return 1
end
return 0
