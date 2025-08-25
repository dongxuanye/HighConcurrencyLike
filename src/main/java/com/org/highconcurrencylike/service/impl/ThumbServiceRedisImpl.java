package com.org.highconcurrencylike.service.impl;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.org.highconcurrencylike.constant.RedisLuaScriptConstant;
import com.org.highconcurrencylike.constant.ThumbConstant;
import com.org.highconcurrencylike.mapper.ThumbMapper;
import com.org.highconcurrencylike.model.dto.thumb.DoThumbRequest;
import com.org.highconcurrencylike.model.entity.Thumb;
import com.org.highconcurrencylike.model.entity.User;
import com.org.highconcurrencylike.model.enums.LuaStatusEnum;
import com.org.highconcurrencylike.service.ThumbService;
import com.org.highconcurrencylike.utils.RedisKeyUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Arrays;

/**
* @author konna
 *
 * redis 实现升级版
 * 与原来的逻辑相比，删除了事务和锁，使用Lua脚本保证点赞和取消点赞操作的原子性，提升接口性能。
*/
@Service("ThumbServiceRedis") // 这里可以，按照名字注入使用
@Slf4j
@RequiredArgsConstructor
public class ThumbServiceRedisImpl extends ServiceImpl<ThumbMapper, Thumb>
    implements ThumbService{

    private final UserServiceImpl userService;

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 点赞
     * 使用lua脚本把锁和事务都替代掉了
     * 目的是使用redis替换掉数据库临时存储数据
     * 后面接入定时任务对数据进行落库
     */
    @Override
    public Boolean doThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        if (doThumbRequest == null || doThumbRequest.getBlogId() == null){
            throw new RuntimeException("参数错误");
        }
        User loginUser = userService.getLoginUser(request);
        Long blogId = doThumbRequest.getBlogId( );

        String timeSlice = getTimeSlice( );
        // Redis Key
        String tempThumbKey = RedisKeyUtil.getTempThumbKey(timeSlice);
        String userThumbKey = RedisKeyUtil.getUserThumbKey(loginUser.getId( ));

        // 执行lua脚本
        long result = redisTemplate.execute(RedisLuaScriptConstant.THUMB_SCRIPT,
                Arrays.asList(tempThumbKey, userThumbKey),
                loginUser.getId( ),
                blogId);

        // 判断是否更新成功
        if (LuaStatusEnum.FAIL.getValue() == result){
            throw new RuntimeException("用户已经点赞！");
        }

        // 更新成功才执行
        return LuaStatusEnum.SUCCESS.getValue() == result;
    }

    @Override
    public Boolean undoThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        if (doThumbRequest == null || doThumbRequest.getBlogId() == null){
            throw new RuntimeException("参数错误");
        }
        User loginUser = userService.getLoginUser(request);

        Long blogId = doThumbRequest.getBlogId( );
        // 计算时间片
        String timeSlice = getTimeSlice( );
        // Redis Key
        String tempThumbKey = RedisKeyUtil.getTempThumbKey(timeSlice);
        String userThumbKey = RedisKeyUtil.getUserThumbKey(loginUser.getId( ));

        // 执行lua脚本
        long result = redisTemplate.execute(RedisLuaScriptConstant.UNDO_THUMB_SCRIPT,
                Arrays.asList(tempThumbKey, userThumbKey),
                loginUser.getId( ),
                blogId);

        // 根据返回值处理结果
        if (LuaStatusEnum.FAIL.getValue() == result){
            throw new RuntimeException("用户没有点赞！");
        }
        return LuaStatusEnum.SUCCESS.getValue() == result;
    }

    /**
     * 获取时间片
     * @return 时间片字符串
     */
    private String getTimeSlice(){
        DateTime newDate = DateUtil.date( );
        // 获取到当前时间最近的整数秒，比如当前 11:20:23，获取到11:20:20
        return DateUtil.format(newDate, "HH:mm") + (DateUtil.second(newDate) / 10) * 10;
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




