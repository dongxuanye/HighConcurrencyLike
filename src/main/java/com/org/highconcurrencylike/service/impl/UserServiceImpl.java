package com.org.highconcurrencylike.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.org.highconcurrencylike.constant.UserConstant;
import com.org.highconcurrencylike.model.entity.User;
import com.org.highconcurrencylike.service.UserService;
import com.org.highconcurrencylike.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/**
* @author konna
* @description 针对表【user】的数据库操作Service实现
* @createDate 2025-08-22 15:11:55
*/
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
    implements UserService{

    @Override
    public User getLoginUser(HttpServletRequest request) {
        return (User) request.getSession().getAttribute(UserConstant.LOGIN_USER);
    }


}




