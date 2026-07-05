package com.smart.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
@Schema(name = "AddressBookDTO", description = "地址簿新增/修改数据传输模型")
public class AddressBookDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "地址ID（修改时传入）")
    private Long id;

    @NotBlank(message = "收货人姓名不能为空")
    @Schema(description = "收货人姓名")
    private String consignee;

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @Schema(description = "收货人手机号")
    private String phone;

    @Schema(description = "性别 0-女 1-男")
    private String sex;

    @NotBlank(message = "省级区划编号不能为空")
    @Schema(description = "省级区划编号")
    private String provinceCode;

    @NotBlank(message = "省级名称不能为空")
    @Schema(description = "省级名称")
    private String provinceName;

    @NotBlank(message = "市级区划编号不能为空")
    @Schema(description = "市级区划编号")
    private String cityCode;

    @NotBlank(message = "市级名称不能为空")
    @Schema(description = "市级名称")
    private String cityName;

    @NotBlank(message = "区级区划编号不能为空")
    @Schema(description = "区级区划编号")
    private String districtCode;

    @NotBlank(message = "区级名称不能为空")
    @Schema(description = "区级名称")
    private String districtName;

    @NotBlank(message = "详细地址不能为空")
    @Schema(description = "详细门牌号地址")
    private String detail;

    @Schema(description = "地址标签（如：家、公司）")
    private String label;

    @NotNull(message = "默认地址状态不能为空")
    @Schema(description = "是否默认地址 0-否 1-是")
    private Integer isDefault;
}