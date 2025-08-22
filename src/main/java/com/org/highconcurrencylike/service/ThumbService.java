package com.org.highconcurrencylike.service;

import com.org.highconcurrencylike.model.dto.thumb.DoThumbRequest;
import com.org.highconcurrencylike.model.entity.Thumb;
import com.baomidou.mybatisplus.extension.service.IService;
import jakarta.servlet.http.HttpServletRequest;

/**
* @author konna
* @description 针对表【thumb】的数据库操作Service
* @createDate 2025-08-22 15:11:55
*/
public interface ThumbService extends IService<Thumb> {

    /**
     * 点赞
     * @param doThumbRequest 点赞请求
     * @param request 请求
     * @return {@link Boolean }
     */
    Boolean doThumb(DoThumbRequest doThumbRequest, HttpServletRequest request);

    /**
     * 取消点赞
     * @param doThumbRequest 取消点赞请求
     * @param request 请求
     * @return {@link Boolean }
     */
    Boolean undoThumb(DoThumbRequest doThumbRequest, HttpServletRequest request);

    /**
     * 是否点赞
     * @param blogId 博客id
     * @param userId 用户id
     * @return {@link Boolean }
     */
    Boolean hasThumb(Long blogId, Long userId);


}
