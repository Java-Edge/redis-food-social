package com.javaedge.commons.model.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * @author apple
 */
@ApiModel(description = "用户积分总排行榜")
@Getter
@Setter
@ToString
public class DinerPointsRankVO extends ShortDinerInfo {

    @ApiModelProperty("总积分")
    private Integer total;

    @ApiModelProperty("排名")
    private Integer ranks;

    @ApiModelProperty(value = "是否为自己", example = "0=否，1=是")
    private Integer isMe;

}