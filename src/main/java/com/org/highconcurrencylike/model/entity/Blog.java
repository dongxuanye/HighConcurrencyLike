package com.org.highconcurrencylike.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * 
 * @TableName blog
 */
@TableName(value ="blog")
@Data
public class Blog implements Serializable {
    /**
     * 
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 
     */
    @TableField(value = "userId")
    private Long userid;

    /**
     * 标题
     */
    @TableField(value = "title")
    private String title;

    /**
     * 封面
     */
    @TableField(value = "coverImg")
    private String coverimg;

    /**
     * 内容
     */
    @TableField(value = "content")
    private String content;

    /**
     * 点赞数
     */
    @TableField(value = "thumbCount")
    private Integer thumbcount;

    /**
     * 创建时间
     */
    @TableField(value = "createTime")
    private Date createtime;

    /**
     * 更新时间
     */
    @TableField(value = "updateTime")
    private Date updatetime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}