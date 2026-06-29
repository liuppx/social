package com.bx.implatform.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "AI回复建议")
public class AiSuggestionsVO {

    @Schema(description = "建议列表")
    private List<String> suggestions;

    @Schema(description = "提供方")
    private String provider;

    @Schema(description = "是否使用模型生成")
    private Boolean modelUsed;
}
