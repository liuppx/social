package com.bx.implatform.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Web3 profile")
public class Web3ProfileVO {

    @Schema(description = "wallet address")
    private String address;

    @Schema(description = "issued timestamp (ms)")
    private Long issuedAt;
}
