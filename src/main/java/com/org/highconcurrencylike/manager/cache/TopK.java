package com.org.highconcurrencylike.manager.cache;

import java.util.List;
import java.util.concurrent.BlockingDeque;

public interface TopK {

    /**
     * HeavyKeeper算法的核心，主要作用是添加元素并更新TopK结构。
     *
     * @param key 键
     * @param increment 增加的数量
     * @return 返回结果
     */
    AddResult add(String key, int increment);

    /**
     * 返回当前TopK元素的列表数据
     *
     * @return 获取数据
     */
    List<Item> list();

    /**
     * 获取被挤出TopK的元素队列
     *
     * @return 获取数据
     */
    BlockingDeque<Item> expelled();

    /**
     * 对所有计数进行衰减
     */
    void fading();

    /**
     * 获取总数
     *
     * @return 获取数据
     */
    long total();
}
