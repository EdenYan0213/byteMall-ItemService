package com.yccc.bytemall.entity.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("category")
@Builder
public class Category {

    /**
     * 分类id
     */
    @TableField("id")
    private Long id;

    /**
     * 分类名称
     */
    private String name;

    /**
     * 商品id
     */
    @TableField("item_id")
    private Long itemId ;
}
