package com.org.highconcurrencylike.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.org.highconcurrencylike.model.dto.thumb.DoThumbRequest;
import com.org.highconcurrencylike.model.entity.Blog;
import com.org.highconcurrencylike.model.entity.Thumb;
import com.org.highconcurrencylike.model.entity.User;
import com.org.highconcurrencylike.model.vo.BlogVO;
import com.org.highconcurrencylike.service.BlogService;
import com.org.highconcurrencylike.mapper.BlogMapper;
import com.org.highconcurrencylike.service.ThumbService;
import com.org.highconcurrencylike.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
* @author konna
* @description 针对表【blog】的数据库操作Service实现
* @createDate 2025-08-22 15:11:55
*/
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog>
    implements BlogService{

    @Resource
    private UserService userService;

    @Lazy
    @Resource(name = "thumbServiceLocalCache")
    private ThumbService thumbService;

    @Override
    public BlogVO getBlogVOById(long blogId, HttpServletRequest request) {
        Blog blog = this.getById(blogId);
        User loginUser = userService.getLoginUser(request);
        return this.getBlogVO(blog, loginUser);
    }

    @Override
    public List<BlogVO> getBlogVOList(List<Blog> blogList, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        HashMap<Long, Boolean> blogIdHasThumbMap = new HashMap<>( );

        if (ObjUtil.isNotEmpty(loginUser)){
            Set<Long> blogIdSet = blogList.stream( ).map(Blog::getId).collect(Collectors.toSet( ));
            List<Thumb> thumbList = thumbService.lambdaQuery( )
                    .eq(Thumb::getUserId, loginUser.getId( ))
                    .in(Thumb::getBlogId, blogIdSet)
                    .list( );

            // 设置一下点赞数
            thumbList.forEach(thumb -> blogIdHasThumbMap.put(thumb.getBlogId(), true));
        }
        // 封装VO进行返回
        return blogList.stream()
                .map(blog -> {
                    BlogVO blogVO = BeanUtil.copyProperties(blog, BlogVO.class);
                    blogVO.setHasThumb(blogIdHasThumbMap.getOrDefault(blog.getId(), false));
                    return blogVO;
                }).toList();
    }

    private BlogVO getBlogVO(Blog blog, User loginUser) {
        BlogVO blogVO = new BlogVO();
        BeanUtil.copyProperties(blog, blogVO);

        if (loginUser == null) {
            return blogVO;
        }

        // 判断是否已点赞
        Boolean exist = thumbService.hasThumb(blog.getId(), loginUser.getId());
        blogVO.setHasThumb(exist);


        return blogVO;
    }
}




