package com.yccc.bytemall.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yccc.bytemall.entity.dto.ItemDTO;
import com.yccc.bytemall.entity.dto.OrderDetailDTO;
import com.yccc.bytemall.entity.po.Item;

import java.util.Collection;
import java.util.List;

public interface IItemService extends IService<Item> {

    /***
     * 根据商品id查询商品信息
     * @param id
     * @return
     */
    ItemDTO queryItemById(Long id);

    void deductStock(List<OrderDetailDTO> items);

    /**
     * 根据商品id集合查询商品信息
     * @param ids
     * @return
     */
    List<ItemDTO> queryItemByIds(Collection<Long> ids);

    /**
     * 根据商品名称查询商品信息
     * @param name
     * @return
     */
    List<ItemDTO> queryItemByName(String name);

    /**
     * 分页查询商品信息
     * @param page
     * @param pageSize
     * @param categoryName
     * @return
     */
    Page<ItemDTO> pageQuery(int page, int pageSize, String categoryName);

    /**
     * 创建商品
     * @param itemDTO
     * @return
     */
    public Long createItem(ItemDTO itemDTO);

    /**
     * 更新商品信息
     * @param itemDTO
     * @return
     */
    boolean updateItem(ItemDTO itemDTO);
}
