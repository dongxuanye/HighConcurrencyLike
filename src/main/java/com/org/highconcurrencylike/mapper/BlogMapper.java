package com.org.highconcurrencylike.mapper;

import com.org.highconcurrencylike.model.entity.Blog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

/**
* @author konna
* @description 针对表【blog】的数据库操作Mapper
* @createDate 2025-08-22 15:11:55
* @Entity generator.domain.Blog
*/
public interface BlogMapper extends BaseMapper<Blog> {

    /**
     * 批量更新点赞数
     */
    void batchUpdateThumbCount(@Param("countMap") Map<Long, Long> countMap);

}




