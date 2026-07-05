package com.smart.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "AddressBookVO", description = "地址簿信息返回数据格式")
public class AddressBookVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "地址ID")
    private Long id;

    @Schema(description = "所属用户ID")
    private Long userId;

    @Schema(description = "收货人姓名")
    private String consignee;

    @Schema(description = "收货人手机号")
    private String phone;

    @Schema(description = "性别 0-女 1-男")
    private String sex;

    @Schema(description = "省级区划编号")
    private String provinceCode;

    @Schema(description = "省级名称")
    private String provinceName;

    @Schema(description = "市级区划编号")
    private String cityCode;

    @Schema(description = "市级名称")
    private String cityName;

    @Schema(description = "区级区划编号")
    private String districtCode;

    @Schema(description = "区级名称")
    private String districtName;

    @Schema(description = "详细门牌号地址")
    private String detail;

    @Schema(description = "地址标签")
    private String label;

    @Schema(description = "是否默认地址 0-否 1-是")
    private Integer isDefault;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}