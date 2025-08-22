package com.org.highconcurrencylike.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.org.highconcurrencylike.model.entity.Thumb;
import com.org.highconcurrencylike.service.ThumbService;
import com.org.highconcurrencylike.mapper.ThumbMapper;
import org.springframework.stereotype.Service;

/**
* @author 榕潮
* @description 针对表【thumb】的数据库操作Service实现
* @createDate 2025-08-22 15:11:55
*/
@Service
public class ThumbServiceImpl extends ServiceImpl<ThumbMapper, Thumb>
    implements ThumbService{

}




