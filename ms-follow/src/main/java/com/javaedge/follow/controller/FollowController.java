package com.javaedge.follow.controller;

import com.javaedge.commons.model.domain.ResultInfo;
import com.javaedge.commons.utils.ResultInfoUtil;
import com.javaedge.follow.service.FollowService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * 关注/取关
 *
 * @author apple
 */
@RestController
public class FollowController {

    @Resource
    private FollowService followService;

    @Resource
    private HttpServletRequest request;

    /**
     * 获取粉丝列表
     *
     * @param dinerId
     * @return
     */
    @GetMapping("followers/{dinerId}")
    public ResultInfo findFollowers(@PathVariable Integer dinerId) {
        return ResultInfoUtil.buildSuccess(request.getServletPath(),
                followService.findFollowers(dinerId));
    }

    /**
     * 共同关注列表
     *
     * @param dinerId
     * @param access_token
     * @return
     */
    @GetMapping("commons/{dinerId}")
    public ResultInfo findCommonsFriends(@PathVariable Integer dinerId,
                                         String access_token) {
        return followService.findCommonsFriends(dinerId, access_token, request.getServletPath());
    }

    /**
     * 关注/取关
     *
     * @param followDinerId 关注的食客ID
     * @param isFollowed    是否关注 1=关注 0=取消
     * @param access_token  登录用户token
     * @return
     */
    @PostMapping("/{followDinerId}")
    public ResultInfo follow(@PathVariable Integer followDinerId,
                             @RequestParam int isFollowed,
                             String access_token) {
        ResultInfo resultInfo = followService.follow(followDinerId, isFollowed, access_token, request.getServletPath());
        return resultInfo;
    }

}