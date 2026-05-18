package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.SystemConstants;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;

/**
 * 校园商铺服务实现类
 *
 * 原项目的店铺模块可以自然改造成校园生活服务模块。
 * Shop 可以表示食堂窗口、校园超市、打印店、快递点、奶茶店、学习空间等。
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    /**
     * 默认附近查询范围：5000 米
     */
    private static final int DEFAULT_GEO_RADIUS = 5000;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    /**
     * 根据 ID 查询校园商铺
     *
     * 使用逻辑过期方式查询缓存。
     * 这样可以减少缓存击穿风险，提高热点商铺访问稳定性。
     *
     * @param id 商铺 ID
     * @return 商铺详情
     */
    @Override
    public Result queryById(Long id) {
        if (id == null || id <= 0) {
            return Result.fail("商铺ID不合法");
        }

        Shop shop = cacheClient.queryWithLogicalExpire(
                CACHE_SHOP_KEY,
                id,
                Shop.class,
                this::getById,
                RedisConstants.CACHE_SHOP_TTL,
                TimeUnit.MINUTES
        );

        if (shop == null) {
            return Result.fail("校园商铺不存在");
        }

        return Result.ok(shop);
    }

    /**
     * 将商铺数据保存到 Redis
     *
     * 这个方法通常用于缓存预热。
     *
     * @param id            商铺 ID
     * @param expireSeconds 逻辑过期秒数
     */
    public void saveShop2Redis(Long id, Long expireSeconds) throws InterruptedException {
        if (id == null || id <= 0) {
            return;
        }

        if (expireSeconds == null || expireSeconds <= 0) {
            expireSeconds = RedisConstants.CACHE_SHOP_TTL * 60;
        }

        Shop shop = getById(id);
        if (shop == null) {
            return;
        }

        Thread.sleep(200);

        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));

        stringRedisTemplate.opsForValue()
                .set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 更新校园商铺信息
     *
     * 更新数据库后删除缓存，保证下一次查询能够重新加载最新数据。
     *
     * @param shop 商铺信息
     * @return 更新结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result update(Shop shop) {
        Result checkResult = checkShopForUpdate(shop);
        if (!checkResult.getSuccess()) {
            return checkResult;
        }

        boolean updated = updateById(shop);
        if (!updated) {
            return Result.fail("更新校园商铺失败");
        }

        deleteShopCache(shop.getId());

        updateShopGeoCache(shop);

        return Result.ok();
    }

    /**
     * 根据类型查询校园商铺
     *
     * 如果没有传入经纬度，使用普通分页查询。
     * 如果传入经纬度，使用 Redis GEO 查询附近商铺。
     *
     * @param typeId  类型 ID
     * @param current 当前页码
     * @param x       经度
     * @param y       纬度
     * @return 商铺列表
     */
    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        if (typeId == null || typeId <= 0) {
            return Result.fail("商铺类型不合法");
        }

        current = normalizeCurrent(current);

        if (x == null || y == null) {
            return queryShopByTypeWithoutGeo(typeId, current);
        }

        return queryShopByTypeWithGeo(typeId, current, x, y);
    }

    /**
     * 不带地理位置的普通分类分页查询
     */
    private Result queryShopByTypeWithoutGeo(Integer typeId, Integer current) {
        Page<Shop> page = lambdaQuery()
                .eq(Shop::getTypeId, typeId.longValue())
                .orderByDesc(Shop::getScore)
                .orderByDesc(Shop::getSold)
                .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));

        return Result.ok(page.getRecords());
    }

    /**
     * 带地理位置的附近校园商铺查询
     */
    private Result queryShopByTypeWithGeo(Integer typeId, Integer current, Double x, Double y) {
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;

        String key = SHOP_GEO_KEY + typeId;

        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                .search(
                        key,
                        GeoReference.fromCoordinate(x, y),
                        new Distance(DEFAULT_GEO_RADIUS),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs()
                                .includeDistance()
                                .limit(end)
                );

        if (results == null) {
            return Result.ok(Collections.emptyList());
        }

        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> geoResultList = results.getContent();
        if (geoResultList == null || geoResultList.size() <= from) {
            return Result.ok(Collections.emptyList());
        }

        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> pageGeoResultList =
                geoResultList.subList(from, geoResultList.size());

        List<Long> ids = new ArrayList<>(pageGeoResultList.size());
        Map<String, Distance> distanceMap = new HashMap<>(pageGeoResultList.size());

        for (GeoResult<RedisGeoCommands.GeoLocation<String>> geoResult : pageGeoResultList) {
            String shopIdStr = geoResult.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            distanceMap.put(shopIdStr, geoResult.getDistance());
        }

        if (ids.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        String idStr = StrUtil.join(",", ids);

        List<Shop> shops = query()
                .in("id", ids)
                .last("order by field(id," + idStr + ")")
                .list();

        fillShopDistance(shops, distanceMap);

        return Result.ok(shops);
    }

    /**
     * 填充商铺距离
     */
    private void fillShopDistance(List<Shop> shops, Map<String, Distance> distanceMap) {
        if (shops == null || shops.isEmpty() || distanceMap == null || distanceMap.isEmpty()) {
            return;
        }

        for (Shop shop : shops) {
            Distance distance = distanceMap.get(shop.getId().toString());
            if (distance != null) {
                shop.setDistance(distance.getValue());
            }
        }
    }

    /**
     * 删除商铺详情缓存
     */
    private void deleteShopCache(Long shopId) {
        if (shopId == null) {
            return;
        }

        stringRedisTemplate.delete(CACHE_SHOP_KEY + shopId);
    }

    /**
     * 更新商铺 GEO 缓存
     *
     * 如果商铺更新时带有经纬度和类型，则同步更新 Redis GEO。
     * 这样附近商铺查询可以尽量保持最新。
     */
    private void updateShopGeoCache(Shop shop) {
        if (shop == null) {
            return;
        }

        if (shop.getId() == null || shop.getTypeId() == null) {
            return;
        }

        if (shop.getX() == null || shop.getY() == null) {
            return;
        }

        stringRedisTemplate.opsForGeo()
                .add(
                        SHOP_GEO_KEY + shop.getTypeId(),
                        new org.springframework.data.geo.Point(shop.getX(), shop.getY()),
                        shop.getId().toString()
                );
    }

    /**
     * 更新商铺前的参数校验
     */
    private Result checkShopForUpdate(Shop shop) {
        if (shop == null) {
            return Result.fail("商铺信息不能为空");
        }

        if (shop.getId() == null || shop.getId() <= 0) {
            return Result.fail("商铺ID不能为空");
        }

        if (shop.getName() != null && StrUtil.isBlank(shop.getName())) {
            return Result.fail("商铺名称不能为空");
        }

        if (shop.getTypeId() != null && shop.getTypeId() <= 0) {
            return Result.fail("商铺类型不合法");
        }

        if ((shop.getX() == null && shop.getY() != null) || (shop.getX() != null && shop.getY() == null)) {
            return Result.fail("经纬度必须同时传入");
        }

        return Result.ok();
    }

    /**
     * 页码归一化
     */
    private Integer normalizeCurrent(Integer current) {
        if (current == null || current < 1) {
            return 1;
        }
        return current;
    }
}