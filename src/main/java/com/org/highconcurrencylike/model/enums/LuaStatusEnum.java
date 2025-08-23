package com.org.highconcurrencylike.model.enums;

import lombok.Getter;

/**
 * lua脚本的执行状态
 */
@Getter
public enum LuaStatusEnum {

    // 成功
    SUCCESS(1L),
    // 失败
    FAIL(-1L),
    ;
    private final long value;

    LuaStatusEnum(long value) {
        this.value = value;
    }

}
