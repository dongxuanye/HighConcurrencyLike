package com.org.highconcurrencylike.constant;

import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * Redis的lua脚本常量
 */
public interface RedisLuaScriptConstant {

    /**
     * 点赞Lua脚本
     * KEY[1]   -- 临时计数键
     * KEY[2]   -- 用户点赞状态键
     * ARGV[1]  -- 用户id
     * ARGV[2]  -- 博客id
     * -1表示已点赞，1表示操作成功
     */
    public static final RedisScript<Long> THUMB_SCRIPT = new DefaultRedisScript<>(
            """
            local tempThumbKey = KEYS[1]    -- 临时计数键（如 thumb:temp:{timeSlice}）
            local userThumbKey = KEYS[2]    -- 用户点赞状态键（如：thumb:{userId}）
            local userId = ARGV[1]          -- 用户id
            local blogId = ARGV[2]          -- 博客id
            
            -- 1.检查是否已点赞（避免重复操作）
            if redis.call('HEXISTS', userThumbKey, blogId) == 1 then
                return -1 -- 已点赞，表示返回失败
            end
            
            -- 2.获取旧值（不存在则默认为0）
            local hashKey = userId .. ':' .. blogId
            local oldNumber = tonumber(redis.call('HGET', tempThumbKey, hashKey) or 0)
            
            -- 3.计算新值
            local newNumber = oldNumber + 1
            
            -- 4.原子性更新：写入临时计数 + 标记用户已点赞
            redis.call('HSET', tempThumbKey, hashKey, newNumber)
            redis.call('HSET', userThumbKey, blogId, 1)
            
            return 1 -- 表示更新成功
            """
            ,Long.class);

    /**
     * 取消点赞Lua脚本
     * KEY[1]   -- 临时计数键
     * KEY[2]   -- 用户点赞状态键
     * ARGV[1]  -- 用户id
     * ARGV[2]  -- 博客id
     * -1表示未点赞，1表示操作成功
     */
    public static final RedisScript<Long> UNDO_THUMB_SCRIPT = new DefaultRedisScript<>(
            """
            local tempThumbKey = KEYS[1]    -- 临时计数键（如 thumb:temp:{timeSlice}）
            local userThumbKey = KEYS[2]    -- 用户点赞状态键（如：thumb:{userId}）
            local userId = ARGV[1]          -- 用户id
            local blogId = ARGV[2]          -- 博客id
            
            -- 1.检查是否已点赞（若未点赞，直接返回失败）
            if redis.call('HEXISTS', userThumbKey, blogId) ~= 1 then
                return -1 -- 未点赞，返回 -1 表示失败
            end
            
            -- 2.获取当前临时计数器（若不存在则默认为0）
            local hashKey = userId .. ':' .. blogId
            local oldNumber = tonumber(redis.call('HGET', tempThumbKey, hashKey) or 0)
            
            -- 3.计算新值并更新
            local newNumber = oldNumber - 1
            
            -- 4.原子性操作：更新临时计数 + 删除用户点赞标记
            redis.call('HSET', tempThumbKey, hashKey, newNumber)
            redis.call('HDEL', userThumbKey, blogId)
            
            return 1 -- 返回 1 表示成功
            """,Long.class);

}
