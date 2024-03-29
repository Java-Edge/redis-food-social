package com.javaedge.diners.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.util.StrUtil;
import com.google.common.collect.Maps;
import com.javaedge.commons.constant.ApiConstant;
import com.javaedge.commons.constant.PointTypesConstant;
import com.javaedge.commons.exception.ParameterException;
import com.javaedge.commons.model.domain.ResultInfo;
import com.javaedge.commons.model.vo.SignInDinerInfo;
import com.javaedge.commons.utils.AssertUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 签到业务逻辑层
 *
 * @author apple
 */
@Service
public class SignService {

    @Value("${service.name.ms-oauth-server}")
    private String oauthServerName;

    @Value("${service.name.ms-points-server}")
    private String pointsServerName;

    @Resource
    private RestTemplate restTemplate;

    @Resource
    private RedisTemplate redisTemplate;

    /**
     * 获取当月签到情况
     *
     * @param accessToken
     * @param dateStr
     * @return
     */
    public Map<String, Boolean> getSignInfo(String accessToken, String dateStr) {
        SignInDinerInfo dinerInfo = loadSignInDinerInfo(accessToken);
        Date date = getDate(dateStr);
        LocalDateTime localDateTime = LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
        // 构建 Key
        String signKey = buildSignKey(dinerInfo.getId(), date);
        // 构建一个自动排序的 Map
        Map<String, Boolean> signInfo = Maps.newTreeMap();
        // 获取某月的总天数（考虑闰年）
        int dayOfMonth = localDateTime.getDayOfMonth();

        // bitfield user:sign:5:202011 u30 0
        BitFieldSubCommands bitFieldSubCommands = BitFieldSubCommands.create()
                .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth))
                .valueAt(0);

        List<Long> list = redisTemplate.opsForValue().bitField(signKey, bitFieldSubCommands);
        if (list == null || list.isEmpty()) {
            return signInfo;
        }
        long value = list.get(0) == null ? 0 : list.get(0);
        // 从低到高位进行遍历，0:未签到,1:已签到
        for (int i = dayOfMonth; i > 0; i--) {
            /**
             * 签到：  yyyy-MM-01 true
             * 未签到：yyyy-MM-01 false
             */
            localDateTime = localDateTime.withDayOfMonth(i);
            boolean flag = value >> 1 << 1 != value;
            signInfo.put(localDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")), flag);
            value >>= 1;
        }
        return signInfo;
    }

    /**
     * 统计用户签到次数
     *
     * @param accessToken
     * @param dateStr
     * @return
     */
    public long getSignCount(String accessToken, String dateStr) {
        SignInDinerInfo dinerInfo = loadSignInDinerInfo(accessToken);
        // 获取日期
        Date date = getDate(dateStr);
        // 构建 Key
        String signKey = buildSignKey(dinerInfo.getId(), date);
        // e.g. BITCOUNT user:sign:5:202011
        return (Long) redisTemplate.execute(
                (RedisCallback<Long>) con -> con.bitCount(signKey.getBytes())
        );
    }

    /**
     * 用户签到
     *
     * @param accessToken
     * @param dateStr
     * @return
     */
    public int doSign(String accessToken, String dateStr) {
        SignInDinerInfo dinerInfo = loadSignInDinerInfo(accessToken);
        // 获取日期
        Date date = getDate(dateStr);
        // 获取日期对应的天数，多少号 从 0 开始
        int offset = DateUtil.dayOfMonth(date) - 1;
        // 构建 Key 【user:sign:5:yyyyMM】
        String signKey = buildSignKey(dinerInfo.getId(), date);
        // 查看是否已签到
        boolean isSigned = redisTemplate.opsForValue().getBit(signKey, offset);
        AssertUtil.isTrue(isSigned, "当前日期已完成签到，无需再签");
        // 【签到】
        redisTemplate.opsForValue().setBit(signKey, offset, true);
        // 【统计连续签到的次数】
        int count = getContinuousSignCount(dinerInfo.getId(), date);
        //【添加签到积分】并返回
        return addPoints(count, dinerInfo.getId());
    }

    /**
     * 统计连续签到的次数
     *
     * @param dinerId
     * @param date
     * @return
     */
    private int getContinuousSignCount(Integer dinerId, Date date) {
        // 获取日期对应的天数，多少号，假设是 30
        int dayOfMonth = DateUtil.dayOfMonth(date);
        // 构建 Key
        String signKey = buildSignKey(dinerId, date);
        // bitfield user:sgin:5:202011 u30 0
        BitFieldSubCommands bitFieldSubCommands = BitFieldSubCommands.create()
                .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth))
                .valueAt(0);
        List<Long> list = redisTemplate.opsForValue().bitField(signKey, bitFieldSubCommands);
        if (list == null || list.isEmpty()) {
            return 0;
        }
        int signCount = 0;
        long v = list.get(0) == null ? 0 : list.get(0);
        // i 表示位移操作次数
        for (int i = dayOfMonth; i > 0; i--) {
            // 右移再左移后等于本身，说明最低位是 0,即未签到
            if (v >> 1 << 1 == v) {
                // 低位 0 且非当天,说明连续签到中断了
                if (i != dayOfMonth) {
                    break;
                }
            } else {
                signCount++;
            }
            // 右移一位并重新赋值，相当于把最低位丢弃一位
            v >>= 1;
        }
        return signCount;
    }

    /**
     * 构建 Key 【user:sign:5:yyyyMM】
     *
     * @param dinerId
     * @param date
     * @return
     */
    private String buildSignKey(Integer dinerId, Date date) {
        return String.format("user:sign:%d:%s", dinerId,
                DateUtil.format(date, "yyyyMM"));
    }

    /**
     * 获取日期
     *
     * @param dateStr 日期入参字符串
     * @return 规定日期格式
     */
    private Date getDate(String dateStr) {
        if (StrUtil.isBlank(dateStr)) {
            return new Date();
        }
        try {
            return DateUtil.parseDate(dateStr);
        } catch (Exception e) {
            throw new ParameterException("请传入yyyy-MM-dd的日期格式");
        }
    }

    /**
     * 获取登录用户信息
     *
     * @param accessToken
     * @return
     */
    private SignInDinerInfo loadSignInDinerInfo(String accessToken) {
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

    /**
     * 添加用户积分
     *
     * @param count         连续签到次数
     * @param signInDinerId 登录用户id
     * @return 获取的积分
     */
    private int addPoints(int count, Integer signInDinerId) {
        // 签到1天送10积分，连续签到2天送20积分，3天送30积分，4天以上均送50积分
        int points = 10;
        if (count == 2) {
            points = 20;
        } else if (count == 3) {
            points = 30;
        } else if (count >= 4) {
            points = 50;
        }
        // 调用积分接口添加积分
        // 构建请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        // 构建请求体（请求参数）
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("dinerId", signInDinerId);
        body.add("points", points);
        body.add("types", PointTypesConstant.sign.getType());
        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
        // 发送请求
        ResponseEntity<ResultInfo> result = restTemplate.postForEntity(pointsServerName,
                entity, ResultInfo.class);
        AssertUtil.isTrue(result.getStatusCode() != HttpStatus.OK, "登录失败！");
        ResultInfo resultInfo = result.getBody();
        if (resultInfo.getCode() != ApiConstant.SUCCESS_CODE) {
            // 失败了, 事物要进行回滚
            throw new ParameterException(resultInfo.getCode(), resultInfo.getMessage());
        }
        return points;
    }

}
