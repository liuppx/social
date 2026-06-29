package com.bx.implatform.service;

import com.bx.implatform.dto.AiRewriteDTO;
import com.bx.implatform.dto.AiSuggestReplyDTO;
import com.bx.implatform.dto.AiSummaryDTO;
import com.bx.implatform.vo.AiSuggestionsVO;
import com.bx.implatform.vo.AiSummaryVO;
import com.bx.implatform.vo.AiTextVO;

public interface AiAssistantService {

    AiTextVO rewrite(AiRewriteDTO dto);

    AiSuggestionsVO suggestReplies(AiSuggestReplyDTO dto);

    AiSummaryVO summarize(AiSummaryDTO dto);
}
