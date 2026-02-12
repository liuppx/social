package com.bx.implatform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
@Schema(description = "Web3 challenge request")
public class Web3ChallengeDTO {

    @NotEmpty(message = "address is required")
    @Pattern(regexp = "^(?i)0x[a-f0-9]{40}$", message = "invalid address")
    @Schema(description = "wallet address")
    private String address;

    @Schema(description = "chain id (optional)")
    private String chainId;
}
