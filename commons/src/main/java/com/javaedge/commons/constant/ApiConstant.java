package com.javaedge.commons.constant;

/**
 * 全局常量类
 *
 * @author apple
 */
public interface ApiConstant {

    /**
     * 成功
     */
    int SUCCESS_CODE = 1;

    /**
     * 成功提示信息
     */
    String SUCCESS_MESSAGE = "Successful.";

    /**
     * 错误
     */
    int ERROR_CODE = 0;

    /**
     * 未登录
     */
    int NO_LOGIN_CODE = -100;

    /**
     * 请登录提示信息
     */
    String NO_LOGIN_MESSAGE = "Please login.";

    /**
     * 错误提示信息
     */
    String ERROR_MESSAGE = "Oops! Something was wrong.";

    /**
     * Feed 默认每页条数
     */
    int PAGE_SIZE = 20;
}