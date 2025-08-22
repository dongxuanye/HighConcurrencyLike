package com.org.highconcurrencylike.service;

import com.org.highconcurrencylike.model.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;
import com.org.highconcurrencylike.model.vo.BlogVO;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
* @author konna
* @description 针对表【blog】的数据库操作Service
* @createDate 2025-08-22 15:11:55
*/
public interface BlogService extends IService<Blog> {

    BlogVO getBlogVOById(long blogId, HttpServletRequest request);

    List<BlogVO> getBlogVOList(List<Blog> blogList, HttpServletRequest request);

}
