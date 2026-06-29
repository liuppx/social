package com.bx.implatform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import java.util.List;

@Data
@Schema(description = "AI回复建议请求")
public class AiSuggestReplyDTO {

    @Schema(description = "会话类型 PRIVATE|GROUP")
    private String chatType;

    @Schema(description = "会话目标id")
    private Long targetId;

    @Valid
    @Size(max = 30, message = "上下文消息最多30条")
    @Schema(description = "最近消息")
    private List<AiContextMessageDTO> messages;

    @Length(max = 32, message = "语气名称不得大于32")
    @Schema(description = "语气 natural|warm|concise|fun")
    private String tone = "natural";
}
