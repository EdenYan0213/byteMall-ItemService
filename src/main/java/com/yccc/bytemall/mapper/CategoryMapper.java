package com.yccc.bytemall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yccc.bytemall.entity.po.Category;
import org.apache.ibatis.annotations.Mapper;


@Mapper
public interface CategoryMapper extends BaseMapper<Category> {
}
