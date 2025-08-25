package com.org.highconcurrencylike.manager.cache;

import lombok.Data;

// 新增返回结果类
@Data
public class AddResult {

    // 被挤出的key
    private final String expiredKey;

    // 当前key是否进入TopK
    private final boolean isHotKey;

    private final String currentKey;

    public AddResult(String expiredKey, boolean isHotKey, String currentKey) {
        this.expiredKey = expiredKey;
        this.isHotKey = isHotKey;
        this.currentKey = currentKey;
    }
}
