package com.bx.implatform.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Web3 challenge response")
public class Web3ChallengeVO {

    @Schema(description = "challenge message")
    private String challenge;

    @Schema(description = "nonce")
    private String nonce;

    @Schema(description = "issued timestamp (ms)")
    private Long issuedAt;

    @Schema(description = "expires timestamp (ms)")
    private Long expiresAt;

    @Schema(description = "chain id")
    private String chainId;
}
