package com.bx.implatform.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "AI会话摘要")
public class AiSummaryVO {

    @Schema(description = "摘要文本")
    private String summary;

    @Schema(description = "重点")
    private List<String> highlights;

    @Schema(description = "待办")
    private List<String> actionItems;

    @Schema(description = "提供方")
    private String provider;

    @Schema(description = "是否使用模型生成")
    private Boolean modelUsed;
}
