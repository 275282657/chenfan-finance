package com.chenfan.finance.model;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * <p>
 * 多图片存储
 * </p>
 *
 * @author lizhejin
 * @since 2021-03-23
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("cf_multy_image")
public class CfMultyImage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 普通帐单内部编号
     */
    @TableId(value = "multy_image_id", type = IdType.AUTO)
    private Long multyImageId;

    /**
     * 业务ID
     */
    private Long businessId;

    /**
     * 类型1:账单
     */
    private Integer businessType;

    /**
     * 名字
     */
    private String fileName;

    /**
     * url
     */
    private String id;


}
