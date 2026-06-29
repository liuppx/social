package com.bx.implatform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "AI摘要请求")
public class AiSummaryDTO {

    @Schema(description = "会话类型 PRIVATE|GROUP")
    private String chatType;

    @Schema(description = "会话目标id")
    private Long targetId;

    @Valid
    @Size(max = 80, message = "摘要上下文消息最多80条")
    @Schema(description = "最近消息")
    private List<AiContextMessageDTO> messages;
}
