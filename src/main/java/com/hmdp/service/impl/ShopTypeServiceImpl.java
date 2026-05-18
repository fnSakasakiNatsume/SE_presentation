package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 校园商铺分类服务实现类
 *
 * 分类可以对应：
 * 1. 校园食堂
 * 2. 奶茶甜品
 * 3. 校园超市
 * 4. 打印复印
 * 5. 快递服务
 * 6. 学习空间
 * 7. 活动摊位
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    /**
     * 分类缓存默认过期时间。
     * 原项目中 RedisConstants.SHOP_TYPE_LONG 存在，但命名不够清晰。
     * 为了减少对常量类的改动，这里直接在业务类中定义一个清晰的缓存时间。
     */
    private static final long SHOP_TYPE_CACHE_TTL = 30L;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 查询商铺分类
     *
     * 优化点：
     * 1. 优先查询 Redis，减少数据库压力；
     * 2. Redis 有数据时直接反序列化返回；
     * 3. Redis 无数据时查询数据库；
     * 4. 数据库查询结果写回 Redis；
     * 5. 写入前先删除旧缓存，避免重复 rightPush 导致分类重复。
     *
     * @return 商铺分类列表
     */
    @Override
    public Result querySort() {
        List<ShopType> cacheData = queryShopTypeFromCache();
        if (!cacheData.isEmpty()) {
            return Result.ok(cacheData);
        }

        List<ShopType> databaseData = queryShopTypeFromDatabase();
        if (databaseData.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        rebuildShopTypeCache(databaseData);

        return Result.ok(databaseData);
    }

    /**
     * 从 Redis 读取分类缓存
     */
    private List<ShopType> queryShopTypeFromCache() {
        List<String> jsonList = stringRedisTemplate.opsForList()
                .range(RedisConstants.SHOP_TYPE_KEY, 0, -1);

        if (jsonList == null || jsonList.isEmpty()) {
            return Collections.emptyList();
        }

        return jsonList.stream()
                .filter(JSONUtil::isTypeJSON)
                .map(json -> JSONUtil.toBean(json, ShopType.class))
                .collect(Collectors.toList());
    }

    /**
     * 从数据库查询分类数据
     */
    private List<ShopType> queryShopTypeFromDatabase() {
        List<ShopType> shopTypes = lambdaQuery()
                .orderByAsc(ShopType::getSort)
                .list();

        if (shopTypes == null || shopTypes.isEmpty()) {
            return Collections.emptyList();
        }

        return shopTypes;
    }

    /**
     * 重建 Redis 分类缓存
     */
    private void rebuildShopTypeCache(List<ShopType> shopTypes) {
        if (shopTypes == null || shopTypes.isEmpty()) {
            return;
        }

        String key = RedisConstants.SHOP_TYPE_KEY;

        stringRedisTemplate.delete(key);

        List<String> jsonList = shopTypes.stream()
                .map(JSONUtil::toJsonStr)
                .collect(Collectors.toList());

        stringRedisTemplate.opsForList().rightPushAll(key, jsonList);
        stringRedisTemplate.expire(key, SHOP_TYPE_CACHE_TTL, TimeUnit.MINUTES);
    }
}