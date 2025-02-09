package com.yccc.bytemall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yccc.bytemall.entity.po.Item;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ItemMapper extends BaseMapper<Item> {
}
