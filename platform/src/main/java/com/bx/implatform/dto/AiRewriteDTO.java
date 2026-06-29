package com.bx.implatform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

@Data
@Schema(description = "AI改写请求")
public class AiRewriteDTO {

    @NotEmpty(message = "改写内容不可为空")
    @Length(max = 2048, message = "改写内容不得大于2048")
    @Schema(description = "待改写文本")
    private String text;

    @Length(max = 32, message = "风格名称不得大于32")
    @Schema(description = "改写风格 friendly|concise|polite|fun")
    private String style = "friendly";
}
