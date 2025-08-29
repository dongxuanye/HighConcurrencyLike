package com.org.highconcurrencylike.job;

import com.google.common.collect.Sets;
import com.org.highconcurrencylike.constant.ThumbConstant;
import com.org.highconcurrencylike.listener.thumb.msg.ThumbEvent;
import com.org.highconcurrencylike.model.entity.Thumb;
import com.org.highconcurrencylike.service.ThumbService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.pulsar.core.PulsarTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 对账任务
 * 1.分批扫描Redis键：使用Redis的SCAN命令代替KEYS，避免阻塞Redis主线程
 * 2.粒度控制：按用户维度进行对账，避免一次加载所有数据
 * 3.差集计算：利用Guava的Sets工具类高效设计Redis和MySQL数据的差异
 */
@Component
@Slf4j
public class ThumbReconcileJob {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private PulsarTemplate<ThumbEvent> pulsarTemplate;

    @Resource(name = "thumbServiceMQ")
    private ThumbService thumbService;

    /**
     * 定时任务入口 对账任务
     */
    @Scheduled(cron = "0 52 16 * * ?")
    public void run(){
        long startTime = System.currentTimeMillis( );

        // 1、获取该分片下的所有用户ID
        Set<Long> userIds = new HashSet<>( );
        String pattern = ThumbConstant.USER_THUMB_KEY_PREFIX + "*";
        try(Cursor<String> cursor = redisTemplate.scan(ScanOptions.scanOptions( ).match(pattern).count(1000).build( ))){
            while (cursor.hasNext( )){
                String key = cursor.next( );
                Long userId = Long.valueOf(key.replace(ThumbConstant.USER_THUMB_KEY_PREFIX, ""));
                userIds.add(userId);
            }
        }

        // 2.逐用户对比
        userIds.forEach(userId -> {
            Set<Long> redisBlogIds = redisTemplate.opsForHash( ).keys(ThumbConstant.USER_THUMB_KEY_PREFIX + userId)
                    .stream( )
                    .map(obj -> Long.valueOf(obj.toString( )))
                    .collect(Collectors.toSet( ));
            Set<Long> mysqlBlogIds = Optional.ofNullable(thumbService.lambdaQuery( )
                            .eq(Thumb::getUserId, userId)
                            .list( )).orElse(new ArrayList<>( ))
                    .stream( )
                    .map(Thumb::getBlogId)
                    .collect(Collectors.toSet( ));

            // 3.计算差异值（redis中有但是MySQL无）
            Set<Long> diffBlogs = Sets.difference(redisBlogIds, mysqlBlogIds);

            log.info("用户={}，差异值={}",userId,diffBlogs);
            // 4.发送补偿事件
            sendCompensationEvents(userId, diffBlogs);
        });

        log.info("对账任务结束，耗时={}ms",System.currentTimeMillis( ) - startTime);
    }

    /**
     * 发送补偿事件到Pulsar
     */
    private void sendCompensationEvents(Long userId, Set<Long> blogIds){
        blogIds.forEach(blogId -> {
            ThumbEvent event = ThumbEvent.builder()
                    .userId(userId)
                    .blogId(blogId)
                    .eventType(ThumbEvent.EventType.INCR)
                    .eventTime(LocalDateTime.now())
                    .build();
            pulsarTemplate.sendAsync(ThumbConstant.THUMB_MQ_TOPIC,event)
                    .exceptionally(ex -> {
                        log.error("发送补偿消息失败:,userId={},blogId={},{}",userId,blogId,ex.getMessage());
                        return null;
                    });
        });
    }
}
