package com.org.highconcurrencylike.controller;

import com.org.highconcurrencylike.common.BaseResponse;
import com.org.highconcurrencylike.common.ResultUtils;
import com.org.highconcurrencylike.constant.UserConstant;
import com.org.highconcurrencylike.model.entity.User;
import com.org.highconcurrencylike.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
public class UserController {  
    @Resource
    private UserService userService;
  
    @GetMapping("/login")
    public BaseResponse<User> login(long userId, HttpServletRequest request) {
        User user = userService.getById(userId);  
        request.getSession().setAttribute(UserConstant.LOGIN_USER, user);
        return ResultUtils.success(user);
    }

    @GetMapping("/get/login")
    public BaseResponse<User> getLoginUser(HttpServletRequest request) {
        User loginUser = (User) request.getSession().getAttribute(UserConstant.LOGIN_USER);
        return ResultUtils.success(loginUser);
    }

}
