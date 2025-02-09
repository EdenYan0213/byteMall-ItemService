package com.yccc.bytemall.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yccc.bytemall.common.api.ApiResult;
import com.yccc.bytemall.entity.dto.ItemDTO;
import com.yccc.bytemall.entity.po.Item;
import com.yccc.bytemall.service.IItemService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/product")
@Slf4j
public class ItemController {

    @Autowired
    private IItemService itemService;

    /**
     * 分页查询商品
     * @param page
     * @param pageSize
     * @return
     */

    @GetMapping("/list")
    public ApiResult<Page<ItemDTO>> queryItemByPage1(@RequestParam("page")int page,
                                                    @RequestParam("pageSize")int pageSize,
                                                    @RequestParam("categoryName")String categoryName){
        log.info("分页查询商品");
        return ApiResult.success(itemService.pageQuery(page,pageSize,categoryName));
    }

    /**
     * 根据商品id查询商品详情
     * @param id
     * @return
     */
    @GetMapping("/{id}")
    public ApiResult<ItemDTO> queryItemById(@PathVariable("id")long id){
        log.info("根据商品id查询商品详情");
        ItemDTO itemDTO = itemService.queryItemById(id);
        if (itemDTO == null){
            return ApiResult.failed("商品不存在");
        }
        return ApiResult.success();
    }

    /**
     * 根据商品名称查询商品详情
     * @param name
     * @return
     */
    @GetMapping("/search")
    public ApiResult<List<ItemDTO>> queryItemByName(@RequestParam("name") String name){
        log.info("根据商品名称查询商品详情");
        return ApiResult.success(itemService.queryItemByName(name));
    }

    /**
     * 创建商品
     * @param itemDTO
     * @return
     */
    @PostMapping("/create")
    public ApiResult createItem(@RequestBody ItemDTO itemDTO){
        log.info("创建商品");
        Long id = itemService.createItem(itemDTO);
        if (id != null) {
            return ApiResult.success(id);
        } else {
            return ApiResult.failed("商品插入失败");
        }
    }

    /**
     * 更新商品
     * @param itemDTO
     * @return
     */
    @PostMapping("/update")
    public ApiResult updateItem(@RequestBody ItemDTO itemDTO){
        log.info("更新商品");
        boolean isSuccess = itemService.updateItem(itemDTO);
        if (isSuccess){
            return ApiResult.success();
        }else {
            return ApiResult.failed("商品更新失败");
        }
    }

    /**
     * 删除商品
     * @return
     */
    @DeleteMapping("/delete/{id}")
    public ApiResult deleteItem(@PathVariable("id") Long id){
        log.info("删除商品");
        boolean isSuccess = itemService.removeById(id);
        if (isSuccess){
            return ApiResult.success();
        }else {
            return ApiResult.failed("商品删除失败");
        }
    }
}
