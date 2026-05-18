package com.hmdp.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.service.IBlogService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;

/**
 * 校园动态控制器
 *
 * 原项目中的 Blog 模块主要是探店笔记。
 * 在校园化改造中，可以将 Blog 理解为“校园动态 / 校园笔记 / 校园探店分享”。
 */
@RestController
@RequestMapping("/blog")
public class BlogController {

    @Resource
    private IBlogService blogService;

    /**
     * 发布校园动态
     *
     * @param blog 动态内容
     * @return 发布结果
     */
    @PostMapping
    public Result saveBlog(@RequestBody Blog blog) {
        Result checkResult = checkBlogForSave(blog);
        if (!checkResult.getSuccess()) {
            return checkResult;
        }

        return blogService.saveBlog(blog);
    }

    /**
     * 点赞或取消点赞校园动态
     *
     * @param id 动态 ID
     * @return 操作结果
     */
    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable("id") Long id) {
        if (id == null || id <= 0) {
            return Result.fail("动态ID不合法");
        }

        return blogService.updateLike(id);
    }

    /**
     * 查询当前登录用户发布的校园动态
     *
     * @param current 当前页码
     * @return 当前用户动态列表
     */
    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        UserDTO user = UserHolder.getUser();
        if (user == null || user.getId() == null) {
            return Result.fail("用户未登录");
        }

        current = normalizeCurrent(current);

        Page<Blog> page = blogService.query()
                .eq("user_id", user.getId())
                .orderByDesc("create_time")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));

        List<Blog> records = page.getRecords();

        return Result.ok(records);
    }

    /**
     * 分页查询热门校园动态
     *
     * @param current 当前页码
     * @return 热门动态列表
     */
    @GetMapping("/hot")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        current = normalizeCurrent(current);
        return blogService.queryHotBlog(current);
    }

    /**
     * 根据 ID 查询校园动态详情
     *
     * @param id 动态 ID
     * @return 动态详情
     */
    @GetMapping("/{id}")
    public Result queryById(@PathVariable("id") Long id) {
        if (id == null || id <= 0) {
            return Result.fail("动态ID不合法");
        }

        return blogService.queryBlogById(id);
    }

    /**
     * 查询校园动态点赞用户列表
     *
     * @param id 动态 ID
     * @return 点赞用户列表
     */
    @GetMapping("/likes/{id}")
    public Result queryBlogLikes(@PathVariable("id") Long id) {
        if (id == null || id <= 0) {
            return Result.fail("动态ID不合法");
        }

        return blogService.queryBlogLikes(id);
    }

    /**
     * 根据用户 ID 查询某个用户发布的校园动态
     *
     * @param current 当前页码
     * @param id      用户 ID
     * @return 用户动态列表
     */
    @GetMapping("/of/user")
    public Result queryBlogByUserId(
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam("id") Long id
    ) {
        if (id == null || id <= 0) {
            return Result.fail("用户ID不合法");
        }

        current = normalizeCurrent(current);

        Page<Blog> page = blogService.query()
                .eq("user_id", id)
                .orderByDesc("create_time")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));

        return Result.ok(page.getRecords());
    }

    /**
     * 查询关注用户的校园动态
     *
     * @param max    上一次查询的最小时间戳
     * @param offset 偏移量
     * @return 关注流动态
     */
    @GetMapping("/of/follow")
    public Result queryBlogOfFollow(
            @RequestParam("lastId") Long max,
            @RequestParam(value = "offset", defaultValue = "0") Integer offset
    ) {
        if (max == null || max <= 0) {
            return Result.fail("时间参数不合法");
        }

        if (offset == null || offset < 0) {
            offset = 0;
        }

        return blogService.queryBlogOfFollow(max, offset);
    }

    /**
     * 查询校园推荐动态
     *
     * 不新增数据库字段，直接根据点赞数、评论数和发布时间排序。
     * 可以放在首页“校园推荐”模块。
     *
     * @param current 当前页码
     * @return 推荐动态列表
     */
    @GetMapping("/campus/recommend")
    public Result queryCampusRecommendBlog(
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ) {
        current = normalizeCurrent(current);

        Page<Blog> page = blogService.query()
                .orderByDesc("liked")
                .orderByDesc("comments")
                .orderByDesc("create_time")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));

        return Result.ok(page.getRecords());
    }

    /**
     * 根据标题关键字搜索校园动态
     *
     * @param keyword 搜索关键字
     * @param current 当前页码
     * @return 搜索结果
     */
    @GetMapping("/campus/search")
    public Result searchCampusBlog(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ) {
        if (StrUtil.isBlank(keyword)) {
            return Result.ok(Collections.emptyList());
        }

        current = normalizeCurrent(current);

        Page<Blog> page = blogService.query()
                .like("title", keyword)
                .or()
                .like("content", keyword)
                .orderByDesc("create_time")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));

        return Result.ok(page.getRecords());
    }

    /**
     * 发布动态参数校验
     */
    private Result checkBlogForSave(Blog blog) {
        if (blog == null) {
            return Result.fail("动态内容不能为空");
        }

        if (StrUtil.isBlank(blog.getTitle())) {
            return Result.fail("动态标题不能为空");
        }

        if (StrUtil.isBlank(blog.getContent())) {
            return Result.fail("动态正文不能为空");
        }

        if (blog.getTitle().length() > 128) {
            return Result.fail("动态标题不能超过128个字符");
        }

        if (blog.getContent().length() > 2000) {
            return Result.fail("动态正文不能超过2000个字符");
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