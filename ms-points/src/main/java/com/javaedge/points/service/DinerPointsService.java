package com.javaedge.points.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.javaedge.commons.constant.ApiConstant;
import com.javaedge.commons.constant.RedisKeyConstant;
import com.javaedge.commons.exception.ParameterException;
import com.javaedge.commons.model.domain.ResultInfo;
import com.javaedge.commons.model.pojo.DinerPoints;
import com.javaedge.commons.model.vo.DinerPointsRankVO;
import com.javaedge.commons.model.vo.ShortDinerInfo;
import com.javaedge.commons.model.vo.SignInDinerInfo;
import com.javaedge.commons.utils.AssertUtil;
import com.javaedge.points.mapper.DinerPointsMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.*;

/**
 * 积分业务
 *
 * @author apple
 */
@Service
@Slf4j
public class DinerPointsService {

    @Resource
    private DinerPointsMapper dinerPointsMapper;

    @Resource
    private RestTemplate restTemplate;

    @Resource
    private RedisTemplate redisTemplate;

    @Value("${service.name.ms-oauth-server}")
    private String oauthServerName;

    @Value("${service.name.ms-diners-server}")
    private String dinersServerName;

    /**
     * 排行榜 Top N
     */
    private static final int TOPN = 20;

    /**
     * 添加积分
     *
     * @param dinerId 食客ID
     * @param points  积分
     * @param types   类型 0=签到，1=关注好友，2=添加Feed，3=添加商户评论
     */
    @Transactional(rollbackFor = Exception.class)
    public void addPoints(Integer dinerId, Integer points, Integer types) {
        AssertUtil.isTrue(dinerId == null || dinerId < 1, "食客不能为空");
        AssertUtil.isTrue(points == null || points < 1, "积分不能为空");
        AssertUtil.isTrue(types == null, "请选择对应的积分类型");

        // 插入MySQL
        DinerPoints dinerPoints = DinerPoints.builder()
                .fkDinerId(dinerId)
                .points(points)
                .types(types).build();
        dinerPointsMapper.save(dinerPoints);

        // 将积分保存到 Redis
        assert dinerId != null;
        redisTemplate.opsForZSet().incrementScore(RedisKeyConstant.diner_points.getKey(), dinerId, points);
    }

    /**
     * Redis
     * 查询前 20 积分排行榜，并显示个人排名
     *
     * @param accessToken
     * @return
     */
    public List<DinerPointsRankVO> getPointRankFromRedis(String accessToken) {
        SignInDinerInfo signInDinerInfo = loadSignInDinerInfo(accessToken);
        // 统计积分排行榜
        Set<ZSetOperations.TypedTuple<Integer>> rangeWithScores = redisTemplate.opsForZSet().reverseRangeWithScores(
                RedisKeyConstant.diner_points.getKey(), 0, 19);
        if (rangeWithScores == null || rangeWithScores.isEmpty()) {
            return Lists.newArrayList();
        }
        // 初始化食客 ID 集合
        List<Integer> rankDinerIds = Lists.newArrayList();
        // 构建 <食客 ID,积分信息> map,需维护顺序
        Map<Integer, DinerPointsRankVO> ranksMap = new LinkedHashMap<>();
        // 初始化排名
        int rank = 1;
        // 循环处理排行榜，添加排名信息
        for (ZSetOperations.TypedTuple<Integer> rangeWithScore : rangeWithScores) {
            // 食客ID
            Integer dinerId = rangeWithScore.getValue();
            // 积分
            int points = Objects.requireNonNull(rangeWithScore.getScore()).intValue();
            // 将食客 ID 添加至食客 ID 集合
            rankDinerIds.add(dinerId);
            DinerPointsRankVO dinerPointsRankVO = new DinerPointsRankVO();
            dinerPointsRankVO.setId(dinerId);
            dinerPointsRankVO.setRanks(rank);
            dinerPointsRankVO.setTotal(points);
            // 将 VO 对象添加至 Map 中
            ranksMap.put(dinerId, dinerPointsRankVO);
            // 排名 +1
            rank++;
        }

        // 获取用户信息
        ResultInfo resultInfo = restTemplate.getForObject(dinersServerName +
                        "findByIds?access_token=${accessToken}&ids={ids}",
                ResultInfo.class, accessToken, StrUtil.join(",", rankDinerIds));
        assert resultInfo != null;
        if (resultInfo.getCode() != ApiConstant.SUCCESS_CODE) {
            throw new ParameterException(resultInfo.getCode(), resultInfo.getMessage());
        }
        List<LinkedHashMap> dinerInfoMaps = (List<LinkedHashMap>) resultInfo.getData();
        // 昵称和头像
        for (LinkedHashMap dinerInfoMap : dinerInfoMaps) {
            ShortDinerInfo shortDinerInfo = BeanUtil.fillBeanWithMap(dinerInfoMap,
                    new ShortDinerInfo(), false);
            DinerPointsRankVO rankVO = ranksMap.get(shortDinerInfo.getId());
            rankVO.setNickname(shortDinerInfo.getNickname());
            rankVO.setAvatarUrl(shortDinerInfo.getAvatarUrl());
        }

        // 判断个人是否在 ranks 中，如果在，添加标记直接返回
        if (ranksMap.containsKey(signInDinerInfo.getId())) {
            DinerPointsRankVO rankVO = ranksMap.get(signInDinerInfo.getId());
            rankVO.setIsMe(1);
            return Lists.newArrayList(ranksMap.values());
        }

        // 如果不在 ranks 中，获取个人排名追加在最后
        // 获取排名
        Long myRank = redisTemplate.opsForZSet().reverseRank(
                RedisKeyConstant.diner_points.getKey(), signInDinerInfo.getId());
        if (myRank != null) {
            DinerPointsRankVO me = new DinerPointsRankVO();
            BeanUtils.copyProperties(signInDinerInfo, me);
            // 排名从 0 开始
            me.setRanks(myRank.intValue() + 1);
            me.setIsMe(1);
            // 获取积分
            Double points = redisTemplate.opsForZSet().score(RedisKeyConstant.diner_points.getKey(),
                    signInDinerInfo.getId());
            assert points != null;
            me.setTotal(points.intValue());
            ranksMap.put(signInDinerInfo.getId(), me);
        }
        return Lists.newArrayList(ranksMap.values());
    }

    /**
     * MySQL
     * 查询前 20 积分排行榜，并显示个人排名
     *
     * @param accessToken
     * @return
     */
    public List<DinerPointsRankVO> findDinerPointRank(String accessToken) {
        SignInDinerInfo signInDinerInfo = loadSignInDinerInfo(accessToken);
        // 统计积分排行榜
        List<DinerPointsRankVO> ranks = dinerPointsMapper.findTopN(TOPN);
        if (ranks == null || ranks.isEmpty()) {
            return Lists.newArrayList();
        }
        // 根据 key：食客 ID value：积分信息 构建一个 Map
        Map<Integer, DinerPointsRankVO> ranksMap = Maps.newLinkedHashMap();
        for (DinerPointsRankVO rank : ranks) {
            ranksMap.put(rank.getId(), rank);
        }
        // 判断个人是否在 ranks 中，如果在，添加标记直接返回
        if (ranksMap.containsKey(signInDinerInfo.getId())) {
            DinerPointsRankVO myRank = ranksMap.get(signInDinerInfo.getId());
            myRank.setIsMe(1);
            return Lists.newArrayList(ranksMap.values());
        }
        // 如果不在 ranks 中，获取个人排名追加在最后
        DinerPointsRankVO myRank = dinerPointsMapper.findDinerRank(signInDinerInfo.getId());
        myRank.setIsMe(1);
        ranks.add(myRank);
        return ranks;
    }

    /**
     * 获取登录用户信息
     *
     * @param accessToken
     * @return
     */
    private SignInDinerInfo loadSignInDinerInfo(String accessToken) {
        // 必须登录
        AssertUtil.mustLogin(accessToken);
        String url = oauthServerName + "user/me?access_token={accessToken}";
        ResultInfo resultInfo = restTemplate.getForObject(url, ResultInfo.class, accessToken);
        assert resultInfo != null;
        if (resultInfo.getCode() != ApiConstant.SUCCESS_CODE) {
            throw new ParameterException(resultInfo.getCode(), resultInfo.getMessage());
        }
        SignInDinerInfo dinerInfo = BeanUtil.fillBeanWithMap((LinkedHashMap) resultInfo.getData(),
                new SignInDinerInfo(), false);
        if (dinerInfo == null) {
            throw new ParameterException(ApiConstant.NO_LOGIN_CODE, ApiConstant.NO_LOGIN_MESSAGE);
        }
        return dinerInfo;
    }

}