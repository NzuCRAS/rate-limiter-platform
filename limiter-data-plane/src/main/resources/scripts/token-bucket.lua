-- Redis Lua 脚本：原子检查并扣减 token bucket
-- KEYS[1]: bucket key (如 "rate_limiter:tenant_001:/api/v1/orders")
-- KEYS[2]: idempotency key (如 "rate_limiter:idempotent:req-123")
-- ARGV[1]: capacity (桶容量)
-- ARGV[2]: refill_rate (每秒补充速率)
-- ARGV[3]: tokens_to_consume (本次消耗)
-- ARGV[4]: now_millis (当前时间戳)
-- ARGV[5]: request_id (幂等ID)
-- ARGV[6]: idempotency_ttl_seconds (幂等key过期时间，如 300)

-- 返回：{allowed, remaining, reason}

local bucket_key = KEYS[1]
local idempotency_key = KEYS[2]

local capacity = tonumber(ARGV[1])
local refill_rate = tonumber(ARGV[2])
local tokens_to_consume = tonumber(ARGV[3])
local now_millis = tonumber(ARGV[4])
local request_id = ARGV[5]
local idempotency_ttl = tonumber(ARGV[6])

-- 1. 检查幂等性
local idempotent_result = redis.call('GET', idempotency_key)
if idempotent_result then
-- 已经处理过该请求，返回之前的结果
	local parts = {}
	for part in string.gmatch(idempotent_result, '([^: ]+)') do
		table.insert(parts, part)
	end
	local prev_allowed = tonumber(parts[1])
	local prev_remaining = tonumber(parts[2])
	local prev_reason = parts[3] or ""
	return {prev_allowed, prev_remaining, prev_reason}
end

-- 2. 获取当前桶状态
local bucket_data = redis.call('HMGET', bucket_key, 'tokens', 'last_refill_time')
local current_tokens = tonumber(bucket_data[1]) or capacity -- 首次访问，给满容量
local last_refill_time = tonumber(bucket_data[2]) or now_millis

-- 3. 计算需要补充的 tokens
local time_elapsed_seconds = (now_millis - last_refill_time) / 1000.0
if time_elapsed_seconds > 0 then
	local tokens_to_add = time_elapsed_seconds * refill_rate
	current_tokens = math.min(capacity, current_tokens + tokens_to_add)
end

-- 4. 检查是否有足够的 tokens
local allowed = 0
local remaining = current_tokens
local reason = ""

if current_tokens >= tokens_to_consume then
-- 允许请求，扣减 tokens
	allowed = 1
	remaining = current_tokens - tokens_to_consume
	reason = ""
else
-- 拒绝请求
	allowed = 0
	remaining = current_tokens
	reason = "quota_exceeded"
end

-- 5. 更新桶状态
redis.call('HMSET', bucket_key,
	'tokens', remaining,
	'last_refill_time', now_millis)
redis.call('EXPIRE', bucket_key, 3600) -- 桶数据1小时后过期

-- 6. 记录幂等结果
local idempotent_value = allowed ..  ":" .. remaining .. ":" ..  reason
redis.call('SETEX', idempotency_key, idempotency_ttl, idempotent_value)

return {allowed, remaining, reason}