package com.org.highconcurrencylike.job;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import com.org.highconcurrencylike.service.impl.ThumbFixDataServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时将Redis中的临时点赞数据同步到数据库
 */
//@Component
@Slf4j
public class SyncThumb2DBJob {

    @Resource
    private ThumbFixDataServiceImpl thumbFixDataService;

    // 10秒执行一次
    @Scheduled(fixedRate = 10000)
    public void run(){
        log.info("定时任务：将Redis中的临时点赞数据同步到数据库");
        DateTime nowDate = DateUtil.date( );
        // 解决小bug，当在50秒 ~ 59秒的时候点赞的话，定时任务刷新的是前10秒的临时点赞，
        // 应该是下一分钟的0 ~ 9秒来刷新，但是-1之后，就变成了刷新-10秒的点赞，redis里面是没有这个键的
        int second = (DateUtil.second(nowDate) / 10 - 1) * 10;
        if (second == -10){
            second = 50;
            // 回到上一分钟
            nowDate = DateUtil.offsetMinute(nowDate, -1);
        }
        String timeSlice = DateUtil.format(nowDate, "HH:mm") + second;
        thumbFixDataService.syncThumb2DBByDate(timeSlice);
        log.info("同步完成，当前时间片：{}",timeSlice);
    }


}
