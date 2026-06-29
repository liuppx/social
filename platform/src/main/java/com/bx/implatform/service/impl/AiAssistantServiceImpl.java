package com.bx.implatform.service.impl;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.bx.implatform.config.props.AiProperties;
import com.bx.implatform.dto.AiContextMessageDTO;
import com.bx.implatform.dto.AiRewriteDTO;
import com.bx.implatform.dto.AiSuggestReplyDTO;
import com.bx.implatform.dto.AiSummaryDTO;
import com.bx.implatform.service.AiAssistantService;
import com.bx.implatform.vo.AiSuggestionsVO;
import com.bx.implatform.vo.AiSummaryVO;
import com.bx.implatform.vo.AiTextVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiAssistantServiceImpl implements AiAssistantService {

    private static final String LOCAL_PROVIDER = "local";
    private static final int DEFAULT_TIMEOUT_MS = 10000;
    private static final Set<String> ACTION_HINTS =
        Set.of("今天", "明天", "今晚", "下周", "安排", "确认", "需要", "记得", "帮我", "麻烦", "todo", "TODO");

    private final AiProperties aiProperties;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Override
    public AiTextVO rewrite(AiRewriteDTO dto) {
        String text = normalize(dto.getText());
        String style = normalizeStyle(dto.getStyle());
        String systemPrompt = "你是一个社交聊天写作助手。只输出改写后的消息，不解释。";
        String userPrompt = "请把下面这句话改写得" + styleLabel(style) + "，保留原意，适合即时通讯发送：\n" + text;
        Optional<String> modelText = callModel(systemPrompt, userPrompt);
        if (modelText.isPresent()) {
            return textResult(cleanOneLine(modelText.get()), modelProvider(), true);
        }
        return textResult(localRewrite(text, style), LOCAL_PROVIDER, false);
    }

    @Override
    public AiSuggestionsVO suggestReplies(AiSuggestReplyDTO dto) {
        List<AiContextMessageDTO> messages = sanitizeMessages(dto.getMessages(), resolveMaxContextMessages());
        String systemPrompt = "你是一个社交聊天回复助手。请给出3条自然、短句、可直接发送的中文回复。";
        String userPrompt = "会话类型：" + safeValue(dto.getChatType()) + "\n语气：" + safeValue(dto.getTone()) + "\n最近消息：\n"
            + renderMessages(messages) + "\n请只输出3条回复建议，每条一行。";
        Optional<String> modelText = callModel(systemPrompt, userPrompt);

        AiSuggestionsVO vo = new AiSuggestionsVO();
        if (modelText.isPresent()) {
            vo.setSuggestions(parseSuggestions(modelText.get()));
            vo.setProvider(modelProvider());
            vo.setModelUsed(true);
            return vo;
        }
        vo.setSuggestions(localSuggestions(messages));
        vo.setProvider(LOCAL_PROVIDER);
        vo.setModelUsed(false);
        return vo;
    }

    @Override
    public AiSummaryVO summarize(AiSummaryDTO dto) {
        List<AiContextMessageDTO> messages = sanitizeMessages(dto.getMessages(), 80);
        LocalSummary localSummary = buildLocalSummary(messages);
        String systemPrompt = "你是一个群聊和私聊摘要助手。请用中文总结消息，不要编造。";
        String userPrompt = "会话类型：" + safeValue(dto.getChatType()) + "\n最近消息：\n" + renderMessages(messages)
            + "\n请输出不超过120字的摘要。";
        Optional<String> modelText = callModel(systemPrompt, userPrompt);

        AiSummaryVO vo = new AiSummaryVO();
        vo.setSummary(modelText.map(this::cleanOneLine).orElse(localSummary.summary()));
        vo.setHighlights(localSummary.highlights());
        vo.setActionItems(localSummary.actionItems());
        vo.setProvider(modelText.isPresent() ? modelProvider() : LOCAL_PROVIDER);
        vo.setModelUsed(modelText.isPresent());
        return vo;
    }

    private Optional<String> callModel(String systemPrompt, String userPrompt) {
        if (!Boolean.TRUE.equals(aiProperties.getEnabled()) || StrUtil.isBlank(aiProperties.getChatCompletionsUrl())) {
            return Optional.empty();
        }
        try {
            JSONObject body = new JSONObject();
            body.put("model", StrUtil.blankToDefault(aiProperties.getModel(), "gpt-4o-mini"));
            body.put("temperature", 0.7);
            JSONArray messages = new JSONArray();
            messages.add(message("system", systemPrompt));
            messages.add(message("user", userPrompt));
            body.put("messages", messages);

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(aiProperties.getChatCompletionsUrl()))
                .timeout(Duration.ofMillis(resolveTimeoutMs()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toJSONString(), StandardCharsets.UTF_8));
            if (StrUtil.isNotBlank(aiProperties.getApiKey())) {
                builder.header("Authorization", "Bearer " + aiProperties.getApiKey());
            }
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("AI模型调用失败,status={}", response.statusCode());
                return Optional.empty();
            }
            JSONObject result = JSON.parseObject(response.body());
            JSONArray choices = result.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                return Optional.empty();
            }
            JSONObject message = choices.getJSONObject(0).getJSONObject("message");
            if (message == null) {
                return Optional.empty();
            }
            String content = message.getString("content");
            return StrUtil.isBlank(content) ? Optional.empty() : Optional.of(content.trim());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {
            log.warn("AI模型调用异常:{}", e.getMessage());
            return Optional.empty();
        }
    }

    private JSONObject message(String role, String content) {
        JSONObject object = new JSONObject();
        object.put("role", role);
        object.put("content", content);
        return object;
    }

    private List<AiContextMessageDTO> sanitizeMessages(List<AiContextMessageDTO> source, int maxSize) {
        List<AiContextMessageDTO> messages = new ArrayList<>();
        if (source == null || source.isEmpty()) {
            return messages;
        }
        int start = Math.max(0, source.size() - maxSize);
        for (int i = start; i < source.size(); i++) {
            AiContextMessageDTO message = source.get(i);
            if (message == null || StrUtil.isBlank(message.getContent())) {
                continue;
            }
            String content = normalize(message.getContent());
            if (looksLikeAttachment(content)) {
                continue;
            }
            AiContextMessageDTO copy = new AiContextMessageDTO();
            copy.setRole(message.getRole());
            copy.setNickName(message.getNickName());
            copy.setSelfSend(message.getSelfSend());
            copy.setSendTime(message.getSendTime());
            copy.setType(message.getType());
            copy.setContent(StrUtil.maxLength(content, 500));
            messages.add(copy);
        }
        return messages;
    }

    private String renderMessages(List<AiContextMessageDTO> messages) {
        if (messages.isEmpty()) {
            return "暂无上下文";
        }
        StringBuilder builder = new StringBuilder();
        for (AiContextMessageDTO message : messages) {
            String name = Boolean.TRUE.equals(message.getSelfSend()) ? "我" : safeValue(message.getNickName());
            builder.append(name).append("：").append(message.getContent()).append('\n');
        }
        return builder.toString();
    }

    private String localRewrite(String text, String style) {
        if ("concise".equals(style)) {
            return cleanOneLine(StrUtil.maxLength(text, 80));
        }
        if ("polite".equals(style)) {
            return cleanOneLine("麻烦你看下，" + text);
        }
        if ("fun".equals(style)) {
            return cleanOneLine(text + "，这个想法挺有意思。");
        }
        return cleanOneLine("我想和你说一下：" + text);
    }

    private List<String> localSuggestions(List<AiContextMessageDTO> messages) {
        String latest = latestOtherMessage(messages);
        List<String> suggestions = new ArrayList<>();
        if (StrUtil.isBlank(latest)) {
            suggestions.add("收到，我看一下。");
            suggestions.add("可以，稍后我回复你。");
            suggestions.add("这个话题挺有意思，我们继续聊。");
            return suggestions;
        }
        if (latest.endsWith("?") || latest.endsWith("？")) {
            suggestions.add("我觉得可以，我们再确认下细节。");
            suggestions.add("这个问题我想一下，稍后给你答复。");
            suggestions.add("可以先按这个方向试试。");
            return suggestions;
        }
        if (containsAny(latest, "谢谢", "感谢")) {
            suggestions.add("不客气，能帮上忙就好。");
            suggestions.add("没问题，有需要再叫我。");
            suggestions.add("小事，后面我们继续同步。");
            return suggestions;
        }
        suggestions.add("收到，我理解你的意思。");
        suggestions.add("这个可以，我们继续往下聊。");
        suggestions.add("我先看下细节，稍后回复你。");
        return suggestions;
    }

    private LocalSummary buildLocalSummary(List<AiContextMessageDTO> messages) {
        if (messages.isEmpty()) {
            return new LocalSummary("最近没有可总结的文字消息。", List.of(), List.of());
        }
        LinkedHashSet<String> speakers = new LinkedHashSet<>();
        List<String> highlights = new ArrayList<>();
        List<String> actionItems = new ArrayList<>();
        for (AiContextMessageDTO message : messages) {
            speakers.add(Boolean.TRUE.equals(message.getSelfSend()) ? "我" : safeValue(message.getNickName()));
            String content = message.getContent();
            if (highlights.size() < 3 && content.length() > 8) {
                highlights.add(StrUtil.maxLength(content, 60));
            }
            if (actionItems.size() < 3 && looksLikeAction(content)) {
                actionItems.add(StrUtil.maxLength(content, 60));
            }
        }
        String latest = messages.get(messages.size() - 1).getContent();
        String summary = "最近 " + speakers.size() + " 位成员围绕当前话题交流了 " + messages.size()
            + " 条消息，最新进展是：" + StrUtil.maxLength(latest, 80);
        return new LocalSummary(summary, highlights, actionItems);
    }

    private List<String> parseSuggestions(String text) {
        List<String> suggestions = new ArrayList<>();
        String normalized = text.trim();
        if (normalized.startsWith("[")) {
            try {
                JSONArray array = JSON.parseArray(normalized);
                for (int i = 0; i < array.size() && suggestions.size() < 3; i++) {
                    addSuggestion(suggestions, array.getString(i));
                }
            } catch (Exception ignored) {
                // fall through to line parser
            }
        }
        if (suggestions.isEmpty()) {
            String[] lines = normalized.split("\\r?\\n");
            for (String line : lines) {
                addSuggestion(suggestions, line);
                if (suggestions.size() >= 3) {
                    break;
                }
            }
        }
        if (suggestions.isEmpty()) {
            addSuggestion(suggestions, normalized);
        }
        return suggestions;
    }

    private void addSuggestion(List<String> suggestions, String raw) {
        String line = cleanOneLine(raw).replaceFirst("^[-*\\d.、）)\\s]+", "");
        if (StrUtil.isNotBlank(line)) {
            suggestions.add(StrUtil.maxLength(line, 120));
        }
    }

    private String latestOtherMessage(List<AiContextMessageDTO> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            AiContextMessageDTO message = messages.get(i);
            if (!Boolean.TRUE.equals(message.getSelfSend()) && StrUtil.isNotBlank(message.getContent())) {
                return message.getContent();
            }
        }
        return "";
    }

    private boolean looksLikeAction(String content) {
        for (String hint : ACTION_HINTS) {
            if (content.contains(hint)) {
                return true;
            }
        }
        return false;
    }

    private boolean looksLikeAttachment(String content) {
        return content.startsWith("{") && content.endsWith("}");
    }

    private boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private AiTextVO textResult(String text, String provider, boolean modelUsed) {
        AiTextVO vo = new AiTextVO();
        vo.setText(text);
        vo.setProvider(provider);
        vo.setModelUsed(modelUsed);
        return vo;
    }

    private String normalize(String text) {
        return StrUtil.blankToDefault(text, "").replace('\u00A0', ' ').trim();
    }

    private String cleanOneLine(String text) {
        String cleaned = normalize(text).replaceAll("[\\r\\n]+", " ");
        if ((cleaned.startsWith("\"") && cleaned.endsWith("\"")) || (cleaned.startsWith("“") && cleaned.endsWith("”"))) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        return cleaned.trim();
    }

    private String normalizeStyle(String style) {
        String value = StrUtil.blankToDefault(style, "friendly").trim().toLowerCase();
        if (Set.of("friendly", "concise", "polite", "fun").contains(value)) {
            return value;
        }
        return "friendly";
    }

    private String styleLabel(String style) {
        if ("concise".equals(style)) {
            return "简洁";
        }
        if ("polite".equals(style)) {
            return "礼貌";
        }
        if ("fun".equals(style)) {
            return "轻松有趣";
        }
        return "自然友好";
    }

    private String safeValue(String value) {
        return StrUtil.blankToDefault(value, "未知");
    }

    private int resolveTimeoutMs() {
        return aiProperties.getTimeoutMs() == null ? DEFAULT_TIMEOUT_MS : aiProperties.getTimeoutMs();
    }

    private int resolveMaxContextMessages() {
        return aiProperties.getMaxContextMessages() == null ? 20 : aiProperties.getMaxContextMessages();
    }

    private String modelProvider() {
        return StrUtil.blankToDefault(aiProperties.getModel(), "model");
    }

    private record LocalSummary(String summary, List<String> highlights, List<String> actionItems) {
    }
}
