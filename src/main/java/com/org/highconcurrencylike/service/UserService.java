package com.org.highconcurrencylike.service;

import com.org.highconcurrencylike.model.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;
import jakarta.servlet.http.HttpServletRequest;

/**
* @author konna
* @description 针对表【user】的数据库操作Service
* @createDate 2025-08-22 15:11:55
*/
public interface UserService extends IService<User> {

    User getLoginUser(HttpServletRequest request);
}
