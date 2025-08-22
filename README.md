# 高并发点赞系统设计

## 1.0
目前使用到的技术栈有springboot3 + mybatis-plus + 声明式事务 + redisson分布式锁 + mysql + knife4j

基础实现用户、点赞、博客等功能

## 2.0
1.引入 spring-session 可以实现分布式登录态，存30分钟

2.利用 jedis + spring-boot-starter-data-redis 实现登录态的持久化存储、使用redis的hash结构替换点赞判断逻辑，减轻数据库压力

### 冷热数据分离拓展：
1.定义存储结构

2.设置值，点赞记录id加上过期时间戳

3.判断是否为blog 的创建时间是否在“一个月前”这个时间点之后。 查询hash结构的数据，不是的话，值为null就代表没有点过赞， 使用instanceof结构类型，比较当前时间和过期，如果大于就使用jdk21特性虚拟线程Thread.ofVirtual().start(执行删除hash操作) 返回false;没有过期并且又是存在的, 返回true

4.不在一个月内，就是mybatis-plus的exist查询返回结果