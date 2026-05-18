package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.USER_SIGN_KEY;

/**
 * 用户服务实现（校园项目：短信验证码登录 + Token 会话 + Redis BitMap 签到）
 *
 * <h2>1. 背景说明（课程设计写法）</h2>
 * <p>
 * 在“校园秒杀/探店”场景中，需要快速完成登录与鉴权：
 * 使用手机号+验证码完成登录，服务端生成 token，并把用户会话存入 Redis（Hash + TTL），
 * 以支撑多节点部署下的“登录态共享”（避免传统 HttpSession 粘性会话问题）。
 * </p>
 *
 * <h2>2. 设计要点</h2>
 * <ol>
 *   <li><b>验证码有效期：</b>验证码写入 Redis 并设置过期时间，避免长期有效。</li>
 *   <li><b>验证码一次性：</b>校验通过后删除验证码 key，防止重复使用（安全性与规范性）。</li>
 *   <li><b>验证码防刷（冷却时间）：</b>同一手机号在冷却窗口内不重复发码（降低短信接口压力）。</li>
 *   <li><b>Token 会话：</b>token 为 key，用户信息以 Hash 形式存储，并设置 TTL，实现“滑动过期/续期”可扩展。</li>
 *   <li><b>签到统计：</b>使用 Redis BitMap 存储“当月每日是否签到”，BitField 一次取回并计算连续签到天数。</li>
 * </ol>
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    /**
     * 验证码有效期：校园项目一般设置 2 分钟，便于演示“过期失效”。
     */
    private static final int LOGIN_CODE_TTL_MINUTES = 2;

    /**
     * Token 有效期：与前端“30 分钟无操作自动退出”类需求一致（演示用）。
     */
    private static final int LOGIN_TOKEN_TTL_MINUTES = 30;

    /**
     * 验证码冷却时间（防刷）：同一手机号在该窗口内重复请求发送验证码会被拒绝。
     * 说明：该策略属于“轻量防刷”，更强的策略可以配合图形验证码/限流组件/Nginx 限速等。
     */
    private static final int LOGIN_CODE_COOLDOWN_SECONDS = 60;

    private static final DateTimeFormatter SIGN_KEY_MONTH_FMT = DateTimeFormatter.ofPattern(":yyyyMM");

    private final StringRedisTemplate stringRedisTemplate;

    public UserServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 发送短信验证码
     *
     * <p>输入约束：手机号必须满足格式校验。</p>
     * <p>失败策略：</p>
     * <ul>
     *   <li>手机号不合法：直接 fail</li>
     *   <li>触发冷却：直接 fail（不再重复写验证码）</li>
     * </ul>
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }

        // 0) 冷却检查：避免频繁请求导致短信接口/Redis 压力上升
        String cooldownKey = loginCodeCooldownKey(phone);
        Boolean absent = stringRedisTemplate.opsForValue()
                .setIfAbsent(cooldownKey, "1", LOGIN_CODE_COOLDOWN_SECONDS, TimeUnit.SECONDS);
        if (absent == null || !absent) {
            return Result.fail("请求过于频繁，请稍后再试");
        }

        // 1) 生成 6 位数字验证码
        String code = RandomUtil.randomNumbers(6);

        // 2) 保存验证码到 Redis，并设置 TTL（防止长期有效）
        stringRedisTemplate.opsForValue().set(
                loginCodeKey(phone),
                code,
                LOGIN_CODE_TTL_MINUTES,
                TimeUnit.MINUTES
        );

        // 3) 模拟发送验证码（课程设计中可注明：生产环境对接短信网关）
        log.info("短信验证码发送成功：{}", code);
        return Result.ok();
    }

    /**
     * 登录（手机号 + 验证码）
     *
     * <h3>过程步骤（实验报告风格）</h3>
     * <ol>
     *   <li>校验手机号格式、验证码非空</li>
     *   <li>从 Redis 读取验证码并比对</li>
     *   <li>验证码通过后删除验证码 key（一次性验证码）</li>
     *   <li>查询/创建用户</li>
     *   <li>生成 token -> 写入 Redis Hash -> 设置 TTL</li>
     * </ol>
     */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        if (loginForm == null) {
            return Result.fail("参数错误");
        }
        String phone = loginForm.getPhone();
        String code = loginForm.getCode();

        // 1) 校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        if (code == null || code.trim().isEmpty()) {
            return Result.fail("验证码不能为空");
        }

        // 2) 校验验证码（Redis）
        String cacheCode = stringRedisTemplate.opsForValue().get(loginCodeKey(phone));
        if (cacheCode == null || !cacheCode.equals(code)) {
            return Result.fail("验证码不一致，请重新输入");
        }

        // 3) 删除验证码（一次性验证码）
        // 说明：避免验证码复用；更符合“安全性/规范性”章节
        stringRedisTemplate.delete(loginCodeKey(phone));

        // 4) 查询用户，不存在则创建
        User user = lambdaQuery().eq(User::getPhone, phone).one();
        if (user == null) {
            user = createUserWithPhone(phone);
        }

        // 5) 生成 token 并写入 Redis（Hash 结构）
        String token = UUID.randomUUID().toString(true);

        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(
                userDTO,
                new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        // fieldValue 可能为 null，需更稳健（避免 NPE）
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue == null ? null : fieldValue.toString())
        );

        String tokenKey = loginUserKey(token);
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        stringRedisTemplate.expire(tokenKey, LOGIN_TOKEN_TTL_MINUTES, TimeUnit.MINUTES);

        return Result.ok(token);
    }

    /**
     * 签到：把当月第 N 天对应 bit 置为 1
     */
    @Override
    public Result sign() {
        Long userId = UserHolder.getUser().getId();
        LocalDateTime now = LocalDateTime.now();

        String key = signKey(userId, now);
        int dayOfMonth = now.getDayOfMonth();

        // Redis bit 下标从 0 开始，因此 dayOfMonth-1
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        return Result.ok();
    }

    /**
     * 统计“连续签到天数”（从今天往前连续）
     *
     * <p>实现思路（校园文档常见写法）：</p>
     * <ol>
     *   <li>用 BitField 一次取回 [0..dayOfMonth-1] 的签到 bit 串（无符号整数）</li>
     *   <li>从最低位开始与 1 做按位与，统计连续的 1 的数量</li>
     * </ol>
     */
    @Override
    public Result signCount() {
        Long userId = UserHolder.getUser().getId();
        LocalDateTime now = LocalDateTime.now();

        String key = signKey(userId, now);
        int dayOfMonth = now.getDayOfMonth();

        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth))
                        .valueAt(0)
        );

        if (result == null || result.isEmpty()) {
            return Result.ok(0);
        }

        Long num = result.get(0);
        if (num == null || num == 0L) {
            return Result.ok(0);
        }

        int count = 0;
        while ((num & 1) == 1) {
            count++;
            num >>>= 1;
        }
        return Result.ok(count);
    }

    // -------------------- private helpers (便于文档说明与单测) --------------------

    private static String loginCodeKey(String phone) {
        return RedisConstants.LOGIN_CODE_KEY + phone;
    }

    /**
     * 验证码发送冷却 key（防刷）
     * <p>示例：login:code:cooldown:13800000000</p>
     */
    private static String loginCodeCooldownKey(String phone) {
        return RedisConstants.LOGIN_CODE_KEY + "cooldown:" + phone;
    }

    private static String loginUserKey(String token) {
        return RedisConstants.LOGIN_USER_KEY + token;
    }

    private static String signKey(Long userId, LocalDateTime now) {
        String keySuffix = now.format(SIGN_KEY_MONTH_FMT);
        return USER_SIGN_KEY + userId + keySuffix;
    }

    /**
     * 创建新用户（校园项目常见：默认昵称 + 随机字符串）
     */
    private User createUserWithPhone(String phone) {
        Objects.requireNonNull(phone, "phone不能为空");
        User user = new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        return user;
    }
}