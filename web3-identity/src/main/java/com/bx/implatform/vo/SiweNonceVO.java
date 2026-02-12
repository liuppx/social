package com.bx.implatform.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "SIWE nonce 返回")
public class SiweNonceVO {

    @Schema(description = "nonce")
    private String nonce;

    @Schema(description = "过期时间(秒)")
    private Integer expiresIn;

    @Schema(description = "链ID")
    private String chainId;
}
