package com.org.highconcurrencylike.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.org.highconcurrencylike.constant.RedisLuaScriptConstant;
import com.org.highconcurrencylike.constant.ThumbConstant;
import com.org.highconcurrencylike.listener.thumb.msg.ThumbEvent;
import com.org.highconcurrencylike.manager.cache.CacheManager;
import com.org.highconcurrencylike.mapper.ThumbMapper;
import com.org.highconcurrencylike.model.dto.thumb.DoThumbRequest;
import com.org.highconcurrencylike.model.entity.Blog;
import com.org.highconcurrencylike.model.entity.Thumb;
import com.org.highconcurrencylike.model.entity.User;
import com.org.highconcurrencylike.model.enums.LuaStatusEnum;
import com.org.highconcurrencylike.service.BlogService;
import com.org.highconcurrencylike.service.ThumbService;
import com.org.highconcurrencylike.service.UserService;
import com.org.highconcurrencylike.utils.RedisKeyUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.pulsar.core.PulsarTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

/**
* @author konna
 *
 * 1.引入消息队列，把点赞操作和后续的数据处理操作解耦，提高扩展性
 * 2.通过点赞操作批量处理消息，来提升性能
 *
*/
@Service("thumbServiceMQ") // 这里可以，按照名字注入使用
@Slf4j
@RequiredArgsConstructor
public class ThumbServiceMQImpl extends ServiceImpl<ThumbMapper, Thumb>
    implements ThumbService{

    private final UserService userService;

    private final RedisTemplate<String, Object> redisTemplate;

    private final PulsarTemplate<ThumbEvent> pulsarTemplate;

    /**
     * 1.检验参数
     * 2.执行lua脚本，未点赞的情况下，增加记录
     * 3.构造事件消息，并发送消息，消息发送失败确保数据一致性处理
     */
    @Override
    public Boolean doThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        if (doThumbRequest == null || doThumbRequest.getBlogId() == null){
            throw new RuntimeException("参数错误");
        }
        User loginUser = userService.getLoginUser(request);
        Long loginUserId = loginUser.getId( );
        Long blogId = doThumbRequest.getBlogId( );
        String userThumbKey = RedisKeyUtil.getUserThumbKey(loginUserId);
        // 执行lua脚本
        long result = redisTemplate.execute(RedisLuaScriptConstant.THUMB_SCRIPT_MQ,
                List.of(userThumbKey),
                blogId);

        if (LuaStatusEnum.FAIL.getValue() == result){
            throw new RuntimeException("用户已经点赞！");
        }
        ThumbEvent thumbEvent = ThumbEvent.builder( )
                .userId(loginUserId)
                .blogId(blogId)
                .eventType(ThumbEvent.EventType.INCR)
                .eventTime(LocalDateTime.now( ))
                .build( );
        // 除了要发送消息，还要考虑消息发送失败的情况
        pulsarTemplate.sendAsync(ThumbConstant.THUMB_MQ_TOPIC,thumbEvent).exceptionally(ex -> {
            // 为了保证数据库和redis一致性，要把点赞记录删掉
            redisTemplate.opsForHash().delete(userThumbKey, blogId.toString(),true);
            log.error("点赞事件发送失败 userId={},blogId={}",loginUserId,blogId,ex);
           return null;
        });
        return true;
    }

    /**
     * 1.检验参数
     * 2.执行lua脚本，已点赞的情况下，删除记录
     * 3.构造事件消息，并发送消息，消息发送失败确保数据一致性处理
     */
    @Override
    public Boolean undoThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        if (doThumbRequest == null || doThumbRequest.getBlogId() == null){
            throw new RuntimeException("参数错误");
        }
        User loginUser = userService.getLoginUser(request);
        Long loginUserId = loginUser.getId( );
        Long blogId = doThumbRequest.getBlogId( );
        String userThumbKey = RedisKeyUtil.getUserThumbKey(loginUserId);
        // 执行lua脚本
        long result = redisTemplate.execute(RedisLuaScriptConstant.UNDO_THUMB_SCRIPT_MQ,
                List.of( userThumbKey ),
                blogId);

        if (LuaStatusEnum.FAIL.getValue() == result){
            throw new RuntimeException("用户没有点赞！");
        }

        ThumbEvent thumbEvent = ThumbEvent.builder( )
                .userId(loginUserId)
                .blogId(blogId)
                .eventType(ThumbEvent.EventType.DECR)
                .eventTime(LocalDateTime.now( ))
                .build( );

        pulsarTemplate.sendAsync(ThumbConstant.THUMB_MQ_TOPIC,thumbEvent).exceptionally(ex -> {
            // 为了保证数据库和redis一致性，要把记录回滚
            redisTemplate.opsForHash().put(userThumbKey, blogId.toString(),true);
            log.error("取消点赞事件发送失败 userId={},blogId={}",loginUserId,blogId,ex);
            return null;
        });
        return true;
    }

    @Override
    public Boolean hasThumb(Long blogId, Long userId) {
        return redisTemplate.opsForHash().hasKey(RedisKeyUtil.getUserThumbKey(userId), blogId.toString());
    }
}




