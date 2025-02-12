package com.yccc.bytemall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yccc.bytemall.entity.dto.ItemDTO;
import com.yccc.bytemall.entity.dto.OrderDetailDTO;
import com.yccc.bytemall.entity.po.Category;
import com.yccc.bytemall.entity.po.Item;
import com.yccc.bytemall.mapper.CategoryMapper;
import com.yccc.bytemall.mapper.ItemMapper;
import com.yccc.bytemall.service.IItemService;
import com.yccc.bytemall.util.BloomFilterUtil;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@Slf4j
public class ItemServiceImpl extends ServiceImpl<ItemMapper, Item> implements IItemService {

    @Autowired
    private CategoryMapper categoryMapper;
    @Autowired
    private RedisTemplate redisTemplate;

    private int exipreTime = 30 * 60 * 1000;

    // 预期插入数量
    static long expectedInsertions = 80000L;
    // 误判率
    static double falseProbability = 0.05;

    // 非法请求所返回的JSON
    static String illegalJson = "[\n" +
            "    \"com.yccc.bytemall.entity.Item\",\n" +
            "    {\n" +
            "        \"id\": null,\n" +
            "        \"name\": \"null\",\n" +
            "        \"description\": null,\n" +
            "        \"price\": null\n" +
            "    }\n" +
            "]";

    private RBloomFilter<Long> bloomFilter = null;

    @Resource
    private BloomFilterUtil bloomFilterUtil;

    @Resource
    private RedissonClient redissonClient;
    @Autowired
    private ItemMapper itemMapper;

    @PostConstruct
    public void init() {
        // 异步初始化布隆过滤器
        CompletableFuture.runAsync(this::initializeBloomFilter);
    }

    @Async
    public void initializeBloomFilter() {
        // 启动项目时初始化bloomFilter
        bloomFilter = bloomFilterUtil.create("itemIdWhiteList", expectedInsertions, falseProbability);

        int pageSize = 100000;
        int pageNumber = 0;
        List<Item> itemsList;

//        do {
            itemsList = itemMapper.selectPage(new Page<>(pageNumber++, pageSize), null).getRecords();
            for (Item item : itemsList) {
                bloomFilter.add(item.getId());
            }
//        } while (!itemsList.isEmpty());

        log.info("布隆过滤器初始化完成");
    }

    @Override
    @Cacheable(cacheNames = "item", key = "#id", unless = "#result == null")
    public ItemDTO queryItemById(Long id) {
        if (!bloomFilter.contains(id)) {
            log.info("所要查询的数据既不在缓存中，也不在数据库中，为非法key");
            redissonClient.getBucket("item::" + id, new StringCodec()).set(illegalJson, new Random().nextInt(200) + 300, TimeUnit.SECONDS);
            return null;
        }
        Item item = null;
        String lockKey = "lock:item::" + id;
        try {
            // 获取锁
            boolean islock = tryLock(lockKey);
            if(!islock){
                //获取锁失败，等待一段时间再重试
                Thread.sleep(50);
                return queryItemById(id);
            }
            // 查询商品信息
            item = baseMapper.selectById(id);
            // 若商品存在，组装商品信息
            if (item == null) {
                // 商品不存在，打印错误日志并返回null
                log.error("商品不存在");
                return null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 释放锁
            unLock(lockKey);
        }
        return itemToItemDTO(item);
    }

    /**
     * 尝试获取锁
     * @param key
     * @return
     */
    private boolean tryLock(String key){
        Boolean flag = redisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return flag != null && flag;
    }

    /**
     * 释放锁
     * @param key
     */
    // 释放锁
    private void unLock(String key){
        redisTemplate.delete(key);
    }

    @Override
    public void deductStock(List<OrderDetailDTO> items) {

    }

    @Override
    public List<ItemDTO> queryItemByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            log.error("ids为空");
            return Collections.emptyList();
        }

        // 构造Redis中的key，规则：item_id
        List<String> keys = ids.stream()
                .map(id -> "item::" + id)
                .collect(Collectors.toList());
        log.info(keys.toString());

        // 使用泛型参数 <String, ItemDTO> 来确保类型安全
        ValueOperations<String, ItemDTO> valueOps = redisTemplate.opsForValue();
        List<ItemDTO> itemDTOs = valueOps.multiGet(keys);

        if (itemDTOs != null && !itemDTOs.isEmpty()) {
            log.info("从Redis中获取商品信息");

            // 过滤掉为null的元素
            List<ItemDTO> filteredItemDTOs = itemDTOs.stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            // 检查是否有缺失的商品信息
            List<Long> missingIds = IntStream.range(0, keys.size())
                    .filter(i -> itemDTOs.get(i) == null)
                    .mapToObj(i -> Long.parseLong(keys.get(i).split("::")[1]))
                    .collect(Collectors.toList());

            if (!missingIds.isEmpty()) {
                // 从数据库中获取缺失的商品信息
                List<Item> missingItems = baseMapper.selectBatchIds(missingIds);
                if (missingItems != null && !missingItems.isEmpty()) {
                    List<ItemDTO> missingItemDTOs = missingItems.stream()
                            .map(this::itemToItemDTO)
                            .collect(Collectors.toList());

                    // 更新Redis缓存并设置过期时间
                    for (int i = 0; i < missingIds.size(); i++) {
                        String key = "item::" + missingIds.get(i);
                        ItemDTO itemDTO = missingItemDTOs.get(i);
                        // 设置过期时间为1小时（3600000毫秒）
                        valueOps.set(key, itemDTO, exipreTime, TimeUnit.MILLISECONDS);
                    }

                    // 将缺失的商品信息添加到结果列表中
                    filteredItemDTOs.addAll(missingItemDTOs);
                }
            }

            return filteredItemDTOs;
        } else {
            // 如果Redis中没有任何数据，则直接从数据库中获取
            List<Item> items = baseMapper.selectBatchIds(ids);
            if (items == null || items.isEmpty()) {
                log.error("商品不存在");
                return Collections.emptyList();
            }

            List<ItemDTO> itemDTOList = items.stream()
                    .map(this::itemToItemDTO)
                    .collect(Collectors.toList());

            // 更新Redis缓存并设置过期时间
            for (int i = 0; i < keys.size(); i++) {
                String key = keys.get(i);
                ItemDTO itemDTO = itemDTOList.get(i);
                // 设置过期时间为1小时（3600000毫秒）
                valueOps.set(key, itemDTO, exipreTime, TimeUnit.MILLISECONDS);
            }

            return itemDTOList;
        }
    }

    @Override
    public List<ItemDTO> queryItemByName(String name) {
        // 判断name是否为空
        if (name != null) {
            List<Item> items = baseMapper.selectList(new QueryWrapper<Item>().like("name", '%' + name + '%'));
            List<ItemDTO> itemDTOList = new ArrayList<>();
            for (Item item : items) {
                ItemDTO itemDTO = itemToItemDTO(item);
                itemDTOList.add(itemDTO);
            }
            return itemDTOList;
        } else {
            // name为空，打印错误日志并返回null
            log.error("name为空");
            return null;
        }
    }

    @Override
    public Page<ItemDTO> pageQuery(int page, int pageSize, String categoryName) {
        if (categoryName != null && !categoryName.isEmpty()) {
            // 查询类别ID
            List<Long> itemIds = categoryMapper.selectList(new QueryWrapper<Category>().eq("name", categoryName))
                    .stream().map(Category::getItemId).collect(Collectors.toList());

            if (itemIds.isEmpty()) {
                log.error("类别不存在");
                return new Page<>();
            }

            // 构建分页对象
            Page<Item> itemPage = new Page<>(page, pageSize);

            // 查询商品列表
            IPage<Item> itemIPage = baseMapper.selectPage(itemPage, new QueryWrapper<Item>()
                    .in("id", itemIds));

            // 转换为ItemDTO列表
            List<ItemDTO> itemDTOList = itemIPage.getRecords().stream()
                    .map(this::itemToItemDTO)
                    .collect(Collectors.toList());

            // 返回分页结果
            Page<ItemDTO> itemDTOPage = new Page<>();
            itemDTOPage.setCurrent(itemIPage.getCurrent());
            itemDTOPage.setSize(itemIPage.getSize());
            itemDTOPage.setTotal(itemIPage.getTotal());
            itemDTOPage.setPages(itemIPage.getPages());
            itemDTOPage.setRecords(itemDTOList);

            return itemDTOPage;
        } else {
            // categoryName为空，打印错误日志并返回空分页结果
            // 构建分页对象
            Page<Item> itemPage = new Page<>(page, pageSize);

            // 查询商品列表
            IPage<Item> itemIPage = baseMapper.selectPage(itemPage, new QueryWrapper<Item>());

            // 转换为ItemDTO列表
            List<ItemDTO> itemDTOList = itemIPage.getRecords().stream()
                    .map(this::itemToItemDTO)
                    .collect(Collectors.toList());

            // 返回分页结果
            Page<ItemDTO> itemDTOPage = new Page<>();
            itemDTOPage.setCurrent(itemIPage.getCurrent());
            itemDTOPage.setSize(itemIPage.getSize());
            itemDTOPage.setTotal(itemIPage.getTotal());
            itemDTOPage.setPages(itemIPage.getPages());
            itemDTOPage.setRecords(itemDTOList);

            return itemDTOPage;
        }
    }

    @Override
    public Long createItem(ItemDTO itemDTO) {
        Item item = Item.builder()
                .name(itemDTO.getName())
                .price(itemDTO.getPrice())
                .image(itemDTO.getImage())
                .brand(itemDTO.getBrand())
                .stock(itemDTO.getStock())
                .description(itemDTO.getDescription())
                .build();
        boolean saved = save(item);
        if (saved) {
            log.info("商品插入成功");
            redisTemplate.opsForValue().set("item::" + item.getId(), itemDTO, exipreTime, TimeUnit.MILLISECONDS);
            for (String categoryName : itemDTO.getCategory()) {
                Category category = Category.builder()
                        .name(categoryName)
                        .itemId(item.getId())
                        .build();
                categoryMapper.insert(category);
            }
            return item.getId();
        } else {
            log.error("商品插入失败");
            return null;
        }
    }

    @Override
    public boolean updateItem(ItemDTO itemDTO) {
        Long id = itemDTO.getId();
        if (id != null) {
            Item item = baseMapper.selectById(id);
            redisTemplate.delete("item::" + id);

            if (item != null) {

                if (itemDTO.getCategory() != null) {
                    // 删除原来的类别
                    categoryMapper.delete(new QueryWrapper<Category>().eq("item_id", id));
                }
                // 构建更新后的商品对象
                item.setName(itemDTO.getName());
                item.setPrice(itemDTO.getPrice());
                item.setImage(itemDTO.getImage());
                item.setBrand(itemDTO.getBrand());
                item.setStock(itemDTO.getStock());
                item.setDescription(itemDTO.getDescription());
                item.setUpdateTime(LocalDateTime.now());

                // 更新商品对应的类别
                for (String categoryName : itemDTO.getCategory()) {
                    Category category = Category.builder()
                            .name(categoryName)
                            .itemId(id)
                            .build();
                    categoryMapper.insert(category);
                }
                redisTemplate.opsForValue().set("item::" + id, itemDTO, exipreTime, TimeUnit.MILLISECONDS);
                return updateById(item);
            }
        } else {
            log.error("商品不存在");
            return false;
        }
        return true;
    }

    @CacheEvict(cacheNames = "item", key = "#id")
    @Override
    public boolean deleteItem(Long id) {
        if (id != null) {
            itemMapper.deleteById(id);
            categoryMapper.delete(new QueryWrapper<Category>().eq("item_id", id));
            return true;
        }
        return false;
    }

    /**
     * 将商品item转换为ItemDTO
     * @param item
     * @return
     */
    private ItemDTO itemToItemDTO(Item item) {
        ItemDTO itemDTO = new ItemDTO();
        itemDTO.setId(item.getId());
        itemDTO.setName(item.getName());
        itemDTO.setPrice(item.getPrice());
        itemDTO.setImage(item.getImage());
        List<Category> category = categoryMapper.selectList(new QueryWrapper<Category>().eq("item_id", item.getId()));
        List<String> categoryName = category.stream().map(Category::getName).collect(Collectors.toList());
        itemDTO.setCategory(categoryName);
        itemDTO.setBrand(item.getBrand());
        return itemDTO;
    }
}
