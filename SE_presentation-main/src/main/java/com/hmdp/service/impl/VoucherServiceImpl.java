package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.mapper.VoucherMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

import static com.hmdp.utils.RedisConstants.SECKILL_STOCK_KEY;

/**
 * 优惠券业务（Service 层实现）
 *
 * <h2>课程/校园项目背景说明</h2>
 * <p>
 * 本模块用于支撑“校园秒杀/抢券”场景：管理员创建秒杀券后，需要将秒杀库存写入 Redis，
 * 以便在高并发秒杀时通过 Redis + Lua 等方式实现“快速校验与扣减”，从而降低 MySQL 压力。
 * </p>
 *
 * <h2>设计要点（文档化说明）</h2>
 * <ol>
 *   <li><b>数据一致性：</b>新增秒杀券时，需要同时落库（MySQL）与写缓存（Redis 库存）。</li>
 *   <li><b>事务边界：</b>优惠券表与秒杀券表写入必须处于同一事务，避免出现“券存在但秒杀信息不存在”。</li>
 *   <li><b>失败策略：</b>任意一步失败都应回滚事务；Redis 写入失败可视为系统异常（需回滚并提示重试）。</li>
 * </ol>
 */
@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements IVoucherService {

    private final ISeckillVoucherService seckillVoucherService;
    private final StringRedisTemplate stringRedisTemplate;

    public VoucherServiceImpl(ISeckillVoucherService seckillVoucherService,
                              StringRedisTemplate stringRedisTemplate) {
        this.seckillVoucherService = seckillVoucherService;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 查询店铺下的优惠券列表（包含普通券/秒杀券信息，具体由 Mapper SQL 决定）
     *
     * @param shopId 店铺ID（不能为空）
     */
    @Override
    public Result queryVoucherOfShop(Long shopId) {
        if (shopId == null) {
            return Result.fail("shopId不能为空");
        }
        List<Voucher> vouchers = getBaseMapper().queryVoucherOfShop(shopId);
        return Result.ok(vouchers);
    }

    /**
     * 新增秒杀券
     *
     * <h3>一致性说明（作业式描述）</h3>
     * <p>
     * 该操作包含三步：<br/>
     * 1）写入 tb_voucher（优惠券基础信息）；<br/>
     * 2）写入 tb_seckill_voucher（秒杀扩展信息：库存、起止时间）；<br/>
     * 3）写入 Redis（秒杀库存缓存：key = {@code SECKILL_STOCK_KEY + voucherId}）。<br/>
     *
     * 三步必须整体成功，否则会导致“缓存与数据库不一致”，从而影响后续秒杀正确性。
     * </p>
     *
     * <h3>事务说明</h3>
     * <p>
     * 使用 Spring 声明式事务，任意异常触发回滚，保证 MySQL 内两张表写入一致性。
     * Redis 写入作为本方法的最后一步，若写入失败同样抛异常回滚，避免产生“数据库已创建，但 Redis 库存未初始化”的状态。
     * </p>
     *
     * @param voucher 秒杀券（必须包含 stock、beginTime、endTime 等字段）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addSeckillVoucher(Voucher voucher) {
        validateSeckillVoucher(voucher);

        // 1) 保存优惠券基础信息
        boolean voucherSaved = save(voucher);
        if (!voucherSaved || voucher.getId() == null) {
            // 这里抛异常触发事务回滚（符合课程项目：失败即回滚，保证一致性）
            throw new IllegalStateException("保存优惠券失败");
        }

        // 2) 保存秒杀扩展信息
        SeckillVoucher seckillVoucher = buildSeckillVoucher(voucher);
        boolean seckillSaved = seckillVoucherService.save(seckillVoucher);
        if (!seckillSaved) {
            throw new IllegalStateException("保存秒杀信息失败");
        }

        // 3) 初始化 Redis 库存（后续秒杀会依赖该值进行快速校验/扣减）
        stringRedisTemplate.opsForValue().set(
                SECKILL_STOCK_KEY + voucher.getId(),
                Objects.toString(voucher.getStock())
        );
    }

    /**
     * 基础参数校验：适合课程项目中“输入约束”章节的实现
     */
    private static void validateSeckillVoucher(Voucher voucher) {
        if (voucher == null) {
            throw new IllegalArgumentException("voucher不能为空");
        }
        if (voucher.getStock() == null || voucher.getStock() < 0) {
            throw new IllegalArgumentException("库存不合法");
        }
        if (voucher.getBeginTime() == null || voucher.getEndTime() == null) {
            throw new IllegalArgumentException("秒杀起止时间不能为空");
        }
        if (voucher.getEndTime().isBefore(voucher.getBeginTime())) {
            throw new IllegalArgumentException("秒杀结束时间不能早于开始时间");
        }
    }

    /**
     * 构造秒杀券扩展信息实体：把“字段映射”集中到一个方法，便于后续维护与单元测试
     */
    private static SeckillVoucher buildSeckillVoucher(Voucher voucher) {
        SeckillVoucher seckillVoucher = new SeckillVoucher();
        seckillVoucher.setVoucherId(voucher.getId());
        seckillVoucher.setStock(voucher.getStock());
        seckillVoucher.setBeginTime(voucher.getBeginTime());
        seckillVoucher.setEndTime(voucher.getEndTime());
        return seckillVoucher;
    }
}