package com.javaedge.commons.model.pojo;

import com.javaedge.commons.model.base.BaseModel;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * @author apple
 */
@ApiModel(description = "食客关注实体类")
@Getter
@Setter
public class Follow extends BaseModel {

    @ApiModelProperty("用户ID")
    private int dinerId;
    @ApiModelProperty("关注用户ID")
    private Integer followDinerId;

}