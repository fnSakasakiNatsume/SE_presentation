package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 关注业务实现类
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    private static final String FOLLOW_KEY_PREFIX = "follows:";

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;

    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        if (followUserId == null) {
            return Result.fail("关注用户不能为空");
        }

        Long userId = UserHolder.getUser().getId();
        if (followUserId.equals(userId)) {
            return Result.fail("不能关注自己");
        }

        if (Boolean.TRUE.equals(isFollow)) {
            return doFollow(userId, followUserId);
        }

        return cancelFollow(userId, followUserId);
    }

    @Override
    public Result isFollow(Long followUserId) {
        if (followUserId == null) {
            return Result.ok(false);
        }

        Long userId = UserHolder.getUser().getId();

        int count = lambdaQuery()
                .eq(Follow::getUserId, userId)
                .eq(Follow::getFollowUserId, followUserId)
                .count();

        return Result.ok(count > 0);
    }

    @Override
    public Result followCommons(Long id) {
        if (id == null) {
            return Result.ok(Collections.emptyList());
        }

        Long userId = UserHolder.getUser().getId();

        Set<String> commonFollowIds = stringRedisTemplate.opsForSet()
                .intersect(buildFollowKey(userId), buildFollowKey(id));

        if (commonFollowIds == null || commonFollowIds.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        List<Long> ids = commonFollowIds.stream()
                .map(Long::valueOf)
                .collect(Collectors.toList());

        List<UserDTO> users = userService.listByIds(ids).stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        return Result.ok(users);
    }

    /**
     * 执行关注操作
     */
    private Result doFollow(Long userId, Long followUserId) {
        boolean exists = lambdaQuery()
                .eq(Follow::getUserId, userId)
                .eq(Follow::getFollowUserId, followUserId)
                .count() > 0;

        if (exists) {
            return Result.ok();
        }

        Follow follow = new Follow()
                .setUserId(userId)
                .setFollowUserId(followUserId)
                .setCreateTime(LocalDateTime.now());

        boolean saved = save(follow);

        if (saved) {
            stringRedisTemplate.opsForSet()
                    .add(buildFollowKey(userId), followUserId.toString());
            return Result.ok();
        }

        return Result.fail("关注失败");
    }

    /**
     * 执行取消关注操作
     */
    private Result cancelFollow(Long userId, Long followUserId) {
        boolean removed = lambdaUpdate()
                .eq(Follow::getUserId, userId)
                .eq(Follow::getFollowUserId, followUserId)
                .remove();

        if (removed) {
            stringRedisTemplate.opsForSet()
                    .remove(buildFollowKey(userId), followUserId.toString());
        }

        return Result.ok();
    }

    private String buildFollowKey(Long userId) {
        return FOLLOW_KEY_PREFIX + userId;
    }
}