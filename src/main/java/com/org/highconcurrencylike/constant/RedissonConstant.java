package com.org.highconcurrencylike.constant;

public interface RedissonConstant {

    // thumb_lock
    String THUMB_LOCK = "thumb_lock:";

    // 等待时间为3秒
    long WAIT_TIME = 3;

    // 持有锁时间为10秒
    long HOLD_TIME = 10;
}
