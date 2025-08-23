package com.org.highconcurrencylike.utils;

import com.org.highconcurrencylike.constant.ThumbConstant;

/**
 * 将Redis要用上键值统一管理
 */
public class RedisKeyUtil {

    /**
     * 获取用户点赞记录key
     */
    public static String getUserThumbKey(Long userId){
        return ThumbConstant.USER_THUMB_KEY_PREFIX + userId;
    }

    /**
     * 获取 临时点赞记录key
     */
    public static String getTempThumbKey(String time){
        return ThumbConstant.TEMP_THUMB_KEY_PREFIX.formatted(time);
    }

}
