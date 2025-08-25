package com.org.highconcurrencylike.manager.cache;

import cn.hutool.core.util.HashUtil;

import java.util.*;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

public class HeavyKeeper implements TopK {

    private static final int LOOKUP_TABLE_SIZE = 256;
    private final int k;
    private final int width;
    private final int depth;
    private final double[] lookupTable;
    private final Bucket[][] buckets;
    private final PriorityQueue<Node> minHeap;
    private final BlockingDeque<Item> expelledQueue;
    private final Random random;
    private long total;
    private final int minCount;

    public HeavyKeeper(int k, int width, int depth, double decay, int minCount ){
        this.k = k;
        this.width = width;
        this.depth = depth;
        this.minCount = minCount;

        // 计算衰减因子的幂次方并存入查找表
        this.lookupTable = new double[LOOKUP_TABLE_SIZE];
        for (int i = 0; i < LOOKUP_TABLE_SIZE; i++) {
            lookupTable[i] = Math.pow(decay, i);
        }

        // 定义桶的深度和宽度, 初始化一波
        this.buckets = new Bucket[depth][width];
        for (int i = 0; i < depth; i++) {
            for (int j = 0; j < width; j++) {
                buckets[i][j] = new Bucket();
            }
        }

        // 定义一个最小堆，并且根据count属性进行排序
        this.minHeap = new PriorityQueue<>(Comparator.comparingInt(n->n.count));
        this.expelledQueue = new LinkedBlockingDeque<>(  );
        this.random = new Random();
        this.total = 0;
    }

    @Override
    public AddResult add(String key, int increment) {
        // 将字符串 key 转换为字节数组，用于哈希计算
        byte[] keyBytes = key.getBytes( );
        // 使用自定义 hash 函数计算 key 的哈希值，作为该 key 的“指纹”。
        int itemFingerprint = hash(keyBytes);
        //  用于记录当前 key 在所有桶中获得的最大计数。
        int maxCount = 0;

        for (int i = 0; i < depth; i++) {
            // 根据索引获取当前桶的对象
            int bucketNumber = Math.abs(hash(keyBytes)) % width;
            Bucket bucket = buckets[i][bucketNumber];

            synchronized(bucket){
                if (bucket.count == 0){ // 桶是空的
                    bucket.fingerprint = itemFingerprint; // 将当前指纹存入桶里
                    bucket.count = increment;
                    maxCount = Math.max(maxCount, increment); // 更新全局最大计数
                } else if (bucket.fingerprint == itemFingerprint) {
                    // 桶非空，且指纹匹配（很可能就是我们要找的 key）
                    bucket.count += increment;  // 将桶的计数增加 increment。
                    maxCount = Math.max(maxCount, bucket.count); // 更新全局最大计数
                }else { // 桶非空，但指纹不匹配（发生了哈希冲突）
                    /**
                     * 这部分是 HeavyKeeper 算法的关键创新点。当发生哈希冲突时，
                     * 它不直接覆盖旧数据，而是通过一个概率衰减机制来决定是否“挤占”
                     * 旧数据。桶的计数越高，被衰减（减一）的概率就越小（decay 值越小）。
                     * 如果桶的计数被衰减到 0，那么旧的 key 就被移除，桶被分配给新的 key
                     */
                    for (int j = 0; j < increment; j++) {
                        // 根据bucket的计数，计算衰减概率。
                        double decay = bucket.count < LOOKUP_TABLE_SIZE ?
                                lookupTable[bucket.count] :
                                lookupTable[LOOKUP_TABLE_SIZE - 1];
                        // 生成一个 0.0 到 1.0 之间的随机数，如果小于 decay，则执行衰减。
                        if (random.nextDouble() < decay){
                            bucket.count --;  // 桶计数减一
                            if (bucket.count == 0){
                                // 如果桶计数减到 0，说明旧的 key 被“踢出”了。
                                bucket.fingerprint = itemFingerprint;
                                bucket.count = increment - j; // 重设 key 的计数（剩余的 increment 次数）
                                maxCount = Math.max(maxCount, bucket.count);   // 更新全局最大计数。
                                break;
                            }
                        }
                    }
                }
            }
        }
        // 将本次增加的次数 increment 加到总访问次数 total 上。
        total += increment;
        // 没有达到预设的最小热点阈值 minCount
        if (maxCount < minCount){
            return new AddResult(null, false, null);
        }
//        System.out.println("为热点数据："+maxCount);
        synchronized (minHeap){
            boolean isHot = false;
            String expelled = null;

            //  检查 key 是否已存在于堆中
            Optional<Node> existing = minHeap.stream( )
                    .filter(n -> n.key.equals(key))
                    .findFirst( );

            if (existing.isPresent()){ // 已经存储
                // 从堆中移除旧的Node
                minHeap.remove(existing.get());
                minHeap.add(new Node(key, maxCount));  // 添加一个新的、更新了计数的 Node
                isHot = true; // 标记为热点
            }else {
                if (minHeap.size() < k || maxCount >= Objects.requireNonNull(minHeap.peek()).count){
                    // 条件一：堆还没满 (minHeap.size() < k)
                    // 条件二：堆满了，但当前 key 的计数大于等于堆中最小计数 (maxCou
                    Node newNode = new Node(key, maxCount); // 创建一个新的 Node
                    if (minHeap.size() >= k){
                        // 堆已满，需要移除一个元素
                        expelled = minHeap.poll().key; // 移除并获取计数最小的 Node 的 key。
                        expelledQueue.offer(new Item(expelled, maxCount));  // 将被移除的 key 放入 expelledQueue。
                    }
                    minHeap.add(newNode); // 将新的 Node 添加到堆中
                    isHot = true; // 标记为热点
                }
            }
            return new AddResult(expelled, isHot, key);
        }
    }

    @Override
    public List<Item> list() {
        // 加锁保证线程安全
        synchronized (minHeap){
            List<Item> result = new ArrayList<>(minHeap.size( ));
            // 遍历最小堆的结点，把结点添加到结果列表中
            for (Node node : minHeap) {
                result.add(new Item(node.key, node.count));
            }
            // 根据count大到小排个序，返回热点数据
            result.sort((a,b)->Integer.compare(b.count(), a.count()));
            return result;
        }
    }

    @Override
    public BlockingDeque<Item> expelled() {
        return expelledQueue;
    }

    @Override
    public void fading() {
        // 桶计数衰减 每个count右移一位(相当于除以2)
        for (Bucket[] row : buckets) {
            for (Bucket bucket : row) {
                synchronized (bucket){
                    bucket.count = bucket.count >> 1;
                }
            }
        }

        // 堆节点衰减，重构最小堆，每个节点的count右移一位(相当于除以2)
        synchronized(minHeap){
            PriorityQueue<Node> newHeap = new PriorityQueue<>(Comparator.comparingInt(n -> n.count));
            for (Node node : minHeap) {
                newHeap.add(new Node(node.key, node.count >> 1));
            }
            minHeap.clear();
            minHeap.addAll(newHeap);
        }

        // 总数衰减，右移一位
        total = total >> 1;
    }

    @Override
    public long total() {
        return total;
    }

    private static class Bucket{
        long fingerprint;
        int count;
    }

    private static class Node{
        final String key;
        final int count;

         Node(String key, int count) {
            this.key = key;
            this.count = count;
        }
    }

    /**
     * 计算hash值,用于快速查找和存储
     * @param data 二进制数组
     * @return 32位的hash值
     */
    private static int hash(byte[] data){
        return HashUtil.murmur32(data);
    }
}
