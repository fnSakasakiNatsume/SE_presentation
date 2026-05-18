package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

/**
 * 关注关系服务
 *
 * <p>用于处理用户关注/取关、关注状态查询、共同关注查询等能力。</p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IFollowService extends IService<Follow> {

    /**
     * 关注 / 取关
     *
     * @param followUserId 被关注用户ID（目标用户）
     * @param isFollow true=关注，false=取关
     * @return Result.ok() / Result.fail(msg)
     */
    Result follow(@NonNull Long followUserId, @NonNull Boolean isFollow);

    /**
     * 查询当前登录用户是否已关注目标用户
     *
     * @param followUserId 目标用户ID
     * @return Result.ok(true/false) 或 Result.fail(msg)
     */
    Result isFollow(@NonNull Long followUserId);

    /**
     * 查询与指定用户的共同关注列表
     *
     * <p>通常返回共同关注的用户简要信息列表（例如 UserDTO 列表）。</p>
     *
     * @param id 另一个用户ID（用于与当前登录用户求共同关注）
     * @return Result.ok(list) 或 Result.fail(msg)
     */
    Result followCommons(@Nullable Long id);
}