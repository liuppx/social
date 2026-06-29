package com.bx.implatform.controller;

import com.bx.implatform.dto.AiRewriteDTO;
import com.bx.implatform.dto.AiSuggestReplyDTO;
import com.bx.implatform.dto.AiSummaryDTO;
import com.bx.implatform.result.Result;
import com.bx.implatform.result.ResultUtils;
import com.bx.implatform.service.AiAssistantService;
import com.bx.implatform.vo.AiSuggestionsVO;
import com.bx.implatform.vo.AiSummaryVO;
import com.bx.implatform.vo.AiTextVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI助手")
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiAssistantController {

    private final AiAssistantService aiAssistantService;

    @PostMapping("/rewrite")
    @Operation(summary = "改写消息", description = "根据指定风格改写输入消息")
    public Result<AiTextVO> rewrite(@Valid @RequestBody AiRewriteDTO dto) {
        return ResultUtils.success(aiAssistantService.rewrite(dto));
    }

    @PostMapping("/reply/suggest")
    @Operation(summary = "回复建议", description = "根据最近消息生成回复建议")
    public Result<AiSuggestionsVO> suggestReplies(@Valid @RequestBody AiSuggestReplyDTO dto) {
        return ResultUtils.success(aiAssistantService.suggestReplies(dto));
    }

    @PostMapping("/summary")
    @Operation(summary = "会话摘要", description = "根据最近消息生成会话摘要")
    public Result<AiSummaryVO> summarize(@Valid @RequestBody AiSummaryDTO dto) {
        return ResultUtils.success(aiAssistantService.summarize(dto));
    }
}
