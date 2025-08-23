package com.org.highconcurrencylike.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.org.highconcurrencylike.constant.RedissonConstant;
import com.org.highconcurrencylike.constant.ThumbConstant;
import com.org.highconcurrencylike.model.dto.thumb.DoThumbRequest;
import com.org.highconcurrencylike.model.entity.Blog;
import com.org.highconcurrencylike.model.entity.Thumb;
import com.org.highconcurrencylike.model.entity.User;
import com.org.highconcurrencylike.service.ThumbService;
import com.org.highconcurrencylike.mapper.ThumbMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.TimeUnit;

/**
* @author konna
* @description 针对表【thumb】的数据库操作Service实现
* @createDate 2025-08-22 15:11:55
*/
@Service("ThumbServiceDB")
@Slf4j
@RequiredArgsConstructor
public class ThumbServiceImpl extends ServiceImpl<ThumbMapper, Thumb>
    implements ThumbService{

    private final UserServiceImpl userService;

    private final BlogServiceImpl blogService;

    private final TransactionTemplate transactionTemplate;

    private final RedissonClient redissonClient;

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public Boolean doThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        if (doThumbRequest == null || doThumbRequest.getBlogId() == null) {
            throw new RuntimeException("参数错误");
        }
        User loginUser = userService.getLoginUser(request);

        // 加个分布式锁，粒度为userId
        // 先获取锁 → 开启事务 → 执行业务逻辑 → 提交事务 → 释放锁
        // 前缀 + 唯一id
        String lockKey = RedissonConstant.THUMB_LOCK + loginUser.getId( );

        // 获取锁
        RLock lock = redissonClient.getLock(lockKey);
        try{
            // 尝试获取锁，等待时间为3秒，持有锁时间为10秒
            boolean isLocked = lock.tryLock(RedissonConstant.WAIT_TIME, RedissonConstant.HOLD_TIME, TimeUnit.SECONDS);
            if (!isLocked) {
                throw new RuntimeException("系统繁忙，请稍后再试");
            }
            // 使用编程式事务
            return transactionTemplate.execute(status -> {
                Long blogId = doThumbRequest.getBlogId( );
                boolean exists = this.hasThumb(blogId, loginUser.getId());
                if (exists){
                    throw new RuntimeException("已经点过赞了");
                }
                // 添加点赞数
                boolean update = blogService.lambdaUpdate( )
                        .eq(Blog::getId, blogId)
                        .setSql("thumbCount = thumbCount + 1")
                        .update( );
                // 添加点赞记录
                Thumb thumb = Thumb.builder( )
                        .userId(loginUser.getId())
                        .blogId(blogId)
                        .build( );
                // 更新成功才做更新
                boolean succeed = update && this.save(thumb);
                if (succeed){
                    // 更新哈希结构
                    // 前缀 + userId为 key，blogId为 hashKey, value为点赞记录的id
                    redisTemplate.opsForHash().put(ThumbConstant.USER_THUMB_KEY_PREFIX + loginUser.getId().toString(), blogId.toString(), thumb.getId());
                    log.info("更新点赞记录成功");
                }
                // 成功才能继续执行
                return succeed;
            });
        }catch (InterruptedException e){
            log.error("获取分布式锁失败", e);
            throw new RuntimeException("系统错误");
        }finally {
            // 释放锁
            if (lock.isHeldByCurrentThread()){
                lock.unlock();
            }
        }
    }

    @Override
    public Boolean undoThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        if (doThumbRequest == null || doThumbRequest.getBlogId() == null) {
            throw new RuntimeException("参数错误");
        }
        User loginUser = userService.getLoginUser(request);

        String lockKey = RedissonConstant.THUMB_LOCK + loginUser.getId();
        RLock lock = redissonClient.getLock(lockKey);
        try{
            boolean isLocked = lock.tryLock(RedissonConstant.WAIT_TIME, RedissonConstant.HOLD_TIME, TimeUnit.SECONDS);
            if (!isLocked) {
                throw new RuntimeException("系统繁忙，请稍后再试");
            }
            return transactionTemplate.execute(status -> {
                Long blogId = doThumbRequest.getBlogId();
                Object thumbIdObj = redisTemplate.opsForHash().get(ThumbConstant.USER_THUMB_KEY_PREFIX + loginUser.getId().toString(), blogId.toString());
                if (thumbIdObj == null) {
                    throw new RuntimeException("用户未点赞");
                }
                Long thumbId = Long.valueOf(thumbIdObj.toString());


                // 减去点赞数
                boolean update = blogService.lambdaUpdate()
                        .eq(Blog::getId, blogId)
                        .setSql("thumbCount = thumbCount - 1")
                        .update();

                boolean success = update && this.removeById(thumbId);

                // 点赞记录从 Redis 删除
                if (success) {
                    redisTemplate.opsForHash().delete(ThumbConstant.USER_THUMB_KEY_PREFIX + loginUser.getId(), blogId.toString());
                    log.info("删除点赞记录成功");
                }
                return success;

            });
        }
        catch (InterruptedException e){
            log.error("获取分布式锁失败", e);
            throw new RuntimeException("系统错误");
        }finally {
            if (lock.isHeldByCurrentThread()){
                lock.unlock();
            }
        }
    }

    /**
     * 判断用户是否点过赞
     * 优点是可以减轻数据的压力
     * 缺点是需要多维护一份数据
     */
    @Override
    public Boolean hasThumb(Long blogId, Long userId) {
        // 前缀 + userId为 key，blogId为 value
        // 判断是否存在
        return redisTemplate.opsForHash().hasKey(ThumbConstant.USER_THUMB_KEY_PREFIX + userId, blogId.toString());
    }
}




