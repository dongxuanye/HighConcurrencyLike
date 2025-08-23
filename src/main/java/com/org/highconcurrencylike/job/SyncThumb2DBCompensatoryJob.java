package com.org.highconcurrencylike.job;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import com.org.highconcurrencylike.service.impl.ThumbFixDataServiceImpl;
import com.org.highconcurrencylike.utils.RedisKeyUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * 补偿操作，放在凌晨操作
 */
@Component
@Slf4j
public class SyncThumb2DBCompensatoryJob {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private ThumbFixDataServiceImpl thumbFixDataService;

    @Scheduled(cron = "0 0 2 * * ?")
    public void run() {
        log.info("开始补偿数据");
        Set<String> thumbKeys = redisTemplate.keys(RedisKeyUtil.getTempThumbKey("") + "*");
        Set<String> needHandleDataSet = new HashSet<>( );
        thumbKeys.stream().filter(ObjUtil::isNotNull).forEach(thumbKey -> {
            needHandleDataSet.add(thumbKey.replace(RedisKeyUtil.getTempThumbKey(""), ""));
        });

        if (CollUtil.isEmpty(needHandleDataSet)){
            log.info("没有数据需要补偿");
            return;
        }
        for (String date : needHandleDataSet) {
            thumbFixDataService.syncThumb2DBByDate(date);
        }
        log.info("临时数据补偿完成");
    }
}
