package com.bx.implatform.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Web3 verify response")
public class Web3VerifyVO {

    @Schema(description = "access token")
    private String token;

    @Schema(description = "wallet address")
    private String address;

    @Schema(description = "refresh token")
    private String refreshToken;

    @Schema(description = "token expires timestamp (ms)")
    private Long expiresAt;

    @Schema(description = "refresh token expires timestamp (ms)")
    private Long refreshExpiresAt;
}
