package com.hmdp.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.utils.SystemConstants;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Collections;

/**
 * 校园商铺控制器
 *
 * 原项目中的 Shop 模块主要表示普通商铺。
 * 这里在不改数据库表结构的前提下，将其扩展为“校园商铺 / 校园服务点”模块，
 * 例如：食堂窗口、奶茶店、打印店、快递点、校园超市、活动摊位等。
 */
@RestController
@RequestMapping("/shop")
public class ShopController {

    @Resource
    private IShopService shopService;

    /**
     * 根据 ID 查询校园商铺详情
     *
     * @param id 商铺 ID
     * @return 商铺详情
     */
    @GetMapping("/{id}")
    public Result queryShopById(@PathVariable("id") Long id) throws InterruptedException {
        if (id == null || id <= 0) {
            return Result.fail("商铺ID不合法");
        }
        return shopService.queryById(id);
    }

    /**
     * 新增校园商铺 / 校园服务点
     *
     * @param shop 商铺信息
     * @return 新增后的商铺 ID
     */
    @PostMapping
    public Result saveShop(@RequestBody Shop shop) {
        Result checkResult = checkShopForSave(shop);
        if (!checkResult.getSuccess()) {
            return checkResult;
        }

        boolean saved = shopService.save(shop);
        if (!saved || shop.getId() == null) {
            return Result.fail("新增校园商铺失败");
        }

        return Result.ok(shop.getId());
    }

    /**
     * 更新校园商铺信息
     *
     * @param shop 商铺信息
     * @return 更新结果
     */
    @PutMapping
    public Result updateShop(@RequestBody Shop shop) {
        if (shop == null || shop.getId() == null || shop.getId() <= 0) {
            return Result.fail("商铺ID不能为空");
        }

        return shopService.update(shop);
    }

    /**
     * 根据商铺类型分页查询校园商铺
     *
     * 如果传入 x、y 坐标，则按照距离查询附近校园商铺；
     * 如果没有传入坐标，则普通分页查询。
     *
     * @param typeId  商铺类型 ID
     * @param current 当前页码
     * @param x       经度
     * @param y       纬度
     * @return 商铺列表
     */
    @GetMapping("/of/type")
    public Result queryShopByType(
            @RequestParam("typeId") Integer typeId,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "x", required = false) Double x,
            @RequestParam(value = "y", required = false) Double y
    ) {
        if (typeId == null || typeId <= 0) {
            return Result.fail("商铺类型不合法");
        }

        current = normalizeCurrent(current);

        if ((x == null && y != null) || (x != null && y == null)) {
            return Result.fail("经纬度参数必须同时传入");
        }

        return shopService.queryShopByType(typeId, current, x, y);
    }

    /**
     * 根据商铺名称关键字分页查询校园商铺
     *
     * @param name    商铺名称关键字
     * @param current 当前页码
     * @return 商铺列表
     */
    @GetMapping("/of/name")
    public Result queryShopByName(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ) {
        current = normalizeCurrent(current);

        Page<Shop> page = shopService.query()
                .like(StrUtil.isNotBlank(name), "name", name)
                .orderByDesc("score")
                .orderByDesc("comments")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));

        return Result.ok(page.getRecords());
    }

    /**
     * 查询推荐校园商铺
     *
     * 这个接口不需要改数据库，直接按照评分、评论数、销量排序。
     * 可以用于首页的“校园推荐商铺”区域。
     *
     * @param current 当前页码
     * @return 推荐商铺列表
     */
    @GetMapping("/campus/recommend")
    public Result queryCampusRecommendShops(
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ) {
        current = normalizeCurrent(current);

        Page<Shop> page = shopService.query()
                .orderByDesc("score")
                .orderByDesc("comments")
                .orderByDesc("sold")
                .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));

        return Result.ok(page.getRecords());
    }

    /**
     * 查询高人气校园商铺
     *
     * 可用于首页展示“校园热门服务点”。
     *
     * @param current 当前页码
     * @return 高人气商铺列表
     */
    @GetMapping("/campus/hot")
    public Result queryHotCampusShops(
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ) {
        current = normalizeCurrent(current);

        Page<Shop> page = shopService.query()
                .orderByDesc("sold")
                .orderByDesc("comments")
                .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));

        return Result.ok(page.getRecords());
    }

    /**
     * 根据区域查询校园商铺
     *
     * 例如：东区、西区、南区、北区、宿舍区、教学区等。
     *
     * @param area    校园区域
     * @param current 当前页码
     * @return 商铺列表
     */
    @GetMapping("/campus/area")
    public Result queryCampusShopByArea(
            @RequestParam(value = "area", required = false) String area,
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ) {
        if (StrUtil.isBlank(area)) {
            return Result.ok(Collections.emptyList());
        }

        current = normalizeCurrent(current);

        Page<Shop> page = shopService.query()
                .eq("area", area)
                .orderByDesc("score")
                .orderByDesc("sold")
                .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));

        return Result.ok(page.getRecords());
    }

    /**
     * 新增商铺参数校验
     */
    private Result checkShopForSave(Shop shop) {
        if (shop == null) {
            return Result.fail("商铺信息不能为空");
        }

        if (StrUtil.isBlank(shop.getName())) {
            return Result.fail("商铺名称不能为空");
        }

        if (shop.getTypeId() == null || shop.getTypeId() <= 0) {
            return Result.fail("商铺类型不能为空");
        }

        if (StrUtil.isBlank(shop.getAddress())) {
            return Result.fail("商铺地址不能为空");
        }

        if (shop.getX() == null || shop.getY() == null) {
            return Result.fail("商铺经纬度不能为空");
        }

        return Result.ok();
    }

    /**
     * 统一处理页码，避免传入 0 或负数导致分页异常
     */
    private Integer normalizeCurrent(Integer current) {
        if (current == null || current < 1) {
            return 1;
        }
        return current;
    }
}