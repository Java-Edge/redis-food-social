package com.javaedge.commons.model.pojo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.javaedge.commons.model.base.BaseModel;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

/**
 * @author apple
 */
@Setter
@Getter
@ApiModel(description = "秒杀代金券信息")
public class SeckillVouchers extends BaseModel {

    @ApiModelProperty("代金券外键")
    private Integer fkVoucherId;

    @ApiModelProperty("数量")
    private Integer amount;

    @ApiModelProperty("秒杀开始时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm", timezone = "GMT+8")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm")
    private Date startTime;

    @ApiModelProperty("秒杀结束时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm", timezone = "GMT+8")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm")
    private Date endTime;
}