package com.bx.implatform.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "AI文本结果")
public class AiTextVO {

    @Schema(description = "文本")
    private String text;

    @Schema(description = "提供方")
    private String provider;

    @Schema(description = "是否使用模型生成")
    private Boolean modelUsed;
}
