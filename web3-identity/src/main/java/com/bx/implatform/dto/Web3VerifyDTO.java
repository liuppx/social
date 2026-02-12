package com.bx.implatform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
@Schema(description = "Web3 challenge verify")
public class Web3VerifyDTO {

    @NotEmpty(message = "address is required")
    @Pattern(regexp = "^(?i)0x[a-f0-9]{40}$", message = "invalid address")
    @Schema(description = "wallet address")
    private String address;

    @NotEmpty(message = "signature is required")
    @Schema(description = "signature")
    private String signature;

    @Schema(description = "terminal type (0:web 1:app 2:pc)")
    private Integer terminal;
}
