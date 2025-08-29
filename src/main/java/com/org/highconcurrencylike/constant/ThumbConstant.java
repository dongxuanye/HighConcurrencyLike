package com.org.highconcurrencylike.constant;

public interface ThumbConstant {

    // 用户点赞键
    String USER_THUMB_KEY_PREFIX = "thumb:";  

    // 临时点赞计数键
    String TEMP_THUMB_KEY_PREFIX = "thumb:temp:%S";

    // 未点赞常量
    Long UN_THUMB_CONSTANT = 0L;

    // 点赞主题
    String THUMB_MQ_TOPIC = "thumb-topic";

    // 死信队列主题
    String THUMB_DLQ_TOPIC = "thumb-dlq-topic";
}
