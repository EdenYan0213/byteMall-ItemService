package com.yccc.bytemall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yccc.bytemall.entity.po.Item;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ItemMapper extends BaseMapper<Item> {

    @Select("select id from item where MATCH(name) AGAINST (${name})")
    List<Long> queryItemByName(String name);
}
