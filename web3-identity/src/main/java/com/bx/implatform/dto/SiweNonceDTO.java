package com.bx.implatform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
@Schema(description = "SIWE获取nonce")
public class SiweNonceDTO {

    @NotEmpty(message = "钱包地址不能为空")
    @Pattern(regexp = "^(?i)0x[a-f0-9]{40}$", message = "钱包地址格式不正确")
    @Schema(description = "钱包地址")
    private String address;

    @Schema(description = "链ID(可选)")
    private String chainId;
}
