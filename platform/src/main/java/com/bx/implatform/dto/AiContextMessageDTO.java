package com.bx.implatform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

@Data
@Schema(description = "AI上下文消息")
public class AiContextMessageDTO {

    @Schema(description = "角色 user|assistant|system")
    private String role;

    @Schema(description = "发送者昵称")
    private String nickName;

    @Length(max = 2048, message = "上下文消息内容不得大于2048")
    @Schema(description = "消息内容")
    private String content;

    @Schema(description = "消息类型")
    private Integer type;

    @Schema(description = "是否自己发送")
    private Boolean selfSend;

    @Schema(description = "发送时间")
    private Long sendTime;
}
