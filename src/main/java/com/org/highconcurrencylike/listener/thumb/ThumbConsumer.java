package com.org.highconcurrencylike.listener.thumb;

import cn.hutool.core.lang.Pair;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.org.highconcurrencylike.constant.ThumbConstant;
import com.org.highconcurrencylike.listener.thumb.msg.ThumbEvent;
import com.org.highconcurrencylike.mapper.BlogMapper;
import com.org.highconcurrencylike.model.entity.Thumb;
import com.org.highconcurrencylike.service.ThumbService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.MessageId;
import org.apache.pulsar.client.api.SubscriptionType;
import org.apache.pulsar.common.schema.SchemaType;
import org.springframework.pulsar.annotation.PulsarListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ThumbConsumer {

    @Resource
    private BlogMapper blogMapper;

    @Resource(name = "thumbServiceMQ")
    private ThumbService thumbService;

    // 处理死信队列
    @PulsarListener(topics = ThumbConstant.THUMB_DLQ_TOPIC)
    public void consumerDlq(Message<ThumbEvent> message){
        MessageId messageId = message.getMessageId( );
        log.info("dlq message = {}", messageId);
        log.info("消息 {} 已入库", messageId);
        log.info("已通知相关人员 {} 处理消息 {}", "康娜酱", messageId);
    }

    // 处理点赞主题
    @PulsarListener(
            subscriptionName = "thumb-subscription",
            topics = ThumbConstant.THUMB_MQ_TOPIC,
            // 批量处理消息
            batch = true,
            consumerCustomizer = "thumbConsumerConfig",
            schemaType = SchemaType.JSON,
            subscriptionType = SubscriptionType.Shared
            ,
            // 配置nack重试策略
            negativeAckRedeliveryBackoff = "negativeAckRedeliveryBackoff",
            // 配置ack超时重试策略
            ackTimeoutRedeliveryBackoff = "ackTimeoutRedeliveryBackoff",
            // 配置死信队列
            deadLetterPolicy = "deadLetterPolicy"
    )
    @Transactional(rollbackFor = Exception.class)
    public void processThumbEvent(List<Message<ThumbEvent>> messages) {
        log.info("ThumbConsumer processBatch: {}", messages.size());
        // 测试死信队列
//        if (true){
//            throw new RuntimeException("批量消费失败!");
//        }

        Map<Long, Long> countMap = new ConcurrentHashMap<>();
        List<Thumb> thumbs = new ArrayList<>();

        // 并行处理消息
        LambdaQueryWrapper<Thumb> wrapper = new LambdaQueryWrapper<>();
        AtomicReference<Boolean> needRemove = new AtomicReference<>(false);

        // 提取消息，并过滤无效消息
        List<ThumbEvent> events = messages.stream( )
                .map(Message::getValue)
                .filter(Objects::nonNull)
                .toList( );

        // 按(userId, blogId)分组，并获取每个分组的最新事件
        Map<Pair<Long, Long>, ThumbEvent> latestEvents = events.stream( )
                .collect(Collectors.groupingBy(
                        e -> Pair.of(e.getUserId( ), e.getBlogId( )),
                        Collectors.collectingAndThen(
                                Collectors.toList( ),
                                list -> {
                                    // 按照时间升序，取最后一个为最新事件
                                    list.sort(Comparator.comparing(ThumbEvent::getEventTime));
                                    if (list.isEmpty( )) {
                                        return null;
                                    }
                                    return list.get(list.size( ) - 1);
                                }
                        )
                ));

        latestEvents.forEach((userBlogPair, event) -> {
            if (event == null) {
                return;
            }
            ThumbEvent.EventType finalAction = event.getEventType();

            if (finalAction == ThumbEvent.EventType.INCR) {
                countMap.merge(event.getBlogId(), 1L, Long::sum);
                Thumb thumb = Thumb.builder( )
                        .userId(event.getUserId( ))
                        .blogId(event.getBlogId( ))
                        .build( );
                thumbs.add(thumb);
            } else {
                needRemove.set(true);
                wrapper.or().eq(Thumb::getUserId, event.getUserId()).eq(Thumb::getBlogId, event.getBlogId());
                countMap.merge(event.getBlogId(), -1L, Long::sum);
            }
        });

        // 批量更新数据库
        if (needRemove.get()) {
            thumbService.remove(wrapper);
            log.info("批量删除点赞记录");
        }
        batchUpdateBlogs(countMap);
        batchInsertThumbs(thumbs);
        log.info("完成批量点赞消息处理");
    }

    public void batchUpdateBlogs(Map<Long, Long> countMap) {
        if (!countMap.isEmpty()) {
            blogMapper.batchUpdateThumbCount(countMap);
        }
    }

    public void batchInsertThumbs(List<Thumb> thumbs) {
        if (!thumbs.isEmpty()) {
            // 分批次插入
            thumbService.saveBatch(thumbs, 500);
        }
    }
}
