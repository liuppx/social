package com.bx.implatform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
@Schema(description = "钱包绑定")
public class WalletLinkDTO {

    @NotEmpty(message = "钱包地址不能为空")
    @Pattern(regexp = "^(?i)0x[a-f0-9]{40}$", message = "钱包地址格式不正确")
    @Schema(description = "钱包地址")
    private String address;

    @NotEmpty(message = "签名不能为空")
    @Schema(description = "签名")
    private String signature;

    @NotEmpty(message = "消息不能为空")
    @Schema(description = "SIWE原文消息")
    private String message;
}
