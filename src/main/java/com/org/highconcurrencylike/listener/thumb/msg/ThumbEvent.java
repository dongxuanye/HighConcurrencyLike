package com.org.highconcurrencylike.listener.thumb.msg;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThumbEvent implements Serializable {

    /**
     * 用户id
     */
    private Long userId;

    /**
     * 博客id
     */
    private Long blogId;

    /**
     * 事件类型
     */
    private EventType eventType;

    /**
     * 事件发生时间
     */
    private LocalDateTime eventTime;

    /**
     * 事件类型枚举
     */
    public enum EventType {
        /**
         * 点赞
         */
        INCR,
        /**
         * 取消点赞
         */
        DECR
    }

}
