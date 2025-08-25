// 新建文件：SyncThumbService.java
package com.org.highconcurrencylike.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.text.StrPool;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.org.highconcurrencylike.mapper.BlogMapper;
import com.org.highconcurrencylike.model.entity.Thumb;
import com.org.highconcurrencylike.model.enums.ThumbTypeEnum;
import com.org.highconcurrencylike.service.ThumbService;
import com.org.highconcurrencylike.utils.RedisKeyUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class ThumbFixDataServiceImpl {

    @Resource(name = "thumbServiceLocalCache")
    private ThumbService thumbService;

    @Resource
    private BlogMapper blogMapper;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Async
    @Transactional(rollbackFor = Exception.class)
    public void syncThumb2DBByDate(String date) {
        String tempThumbKey = RedisKeyUtil.getTempThumbKey(date);
        Map<Object, Object> allTempThumbMap = redisTemplate.opsForHash().entries(tempThumbKey);
        boolean thumbMapEmpty = CollUtil.isEmpty(allTempThumbMap);

        if (thumbMapEmpty) {
            return;
        }

        Map<Long, Long> blogThumbCountMap = new HashMap<>();
        ArrayList<Thumb> thumbList = new ArrayList<>();
        LambdaQueryWrapper<Thumb> wrapper = new LambdaQueryWrapper<>();
        boolean needRemove = false;

        for (Object userIdBlogIdObj : allTempThumbMap.keySet()) {
            String userIdBlogId = (String) userIdBlogIdObj;
            String[] userIdAndBlogId = userIdBlogId.split(StrPool.COLON);
            Long userId = Long.valueOf(userIdAndBlogId[0]);
            Long blogId = Long.valueOf(userIdAndBlogId[1]);
            Integer thumbType = Integer.valueOf(allTempThumbMap.get(userIdBlogId).toString());

            if (ThumbTypeEnum.INCR.getValue() == thumbType) {
                Thumb thumb = Thumb.builder()
                        .userId(userId)
                        .blogId(blogId)
                        .build();
                thumbList.add(thumb);
            } else if (ThumbTypeEnum.DECR.getValue() == thumbType) {
                needRemove = true;
                wrapper.or().eq(Thumb::getUserId, userId)
                        .eq(Thumb::getBlogId, blogId);
            } else {
                if (ThumbTypeEnum.NON.getValue() != thumbType) {
                    log.warn("数据异常：{}", userId + "," + blogId + "," + thumbType);
                }
                continue;
            }

            blogThumbCountMap.put(blogId, blogThumbCountMap.getOrDefault(blogId, 0L) + thumbType);
        }

        // 保存点赞记录，但是大概会有10秒左右的延迟，这个应该是可以容忍
        // 或者使用在存储的时候，把时间也放进来
        thumbService.saveBatch(thumbList);

        if (needRemove) {
            // 取消点赞的移除记录
            thumbService.remove(wrapper);
        }

        if (!blogThumbCountMap.isEmpty()) {
            // 更新博客点赞数
            blogMapper.batchUpdateThumbCount(blogThumbCountMap);
        }

        // 移除临时的点赞记录
        redisTemplate.delete(tempThumbKey);
    }
}
