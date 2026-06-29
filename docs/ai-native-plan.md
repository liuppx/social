# Yeying Social AI Native 改造记录

本文记录 AI Native 改造的第一阶段落地内容和后续方向。

## 第一阶段已落地

### 后端接口

`platform` 新增 AI 助手接口，均复用现有登录鉴权。

- `POST /ai/rewrite`：改写当前输入内容。
- `POST /ai/reply/suggest`：基于最近会话消息生成回复建议。
- `POST /ai/summary`：基于最近会话消息生成会话摘要、重点与待办。

默认没有外部模型配置时会走本地降级逻辑，保证本地开发可用。

### 前端入口

Web 聊天工具栏新增 AI 助手入口：

- 改写输入：读取当前输入框内容，改写后回填。
- 生成回复：读取最近会话上下文，展示可点击回复建议。
- 总结会话：展示最近消息摘要、重点与待办。

## 外部模型配置

默认配置在 `platform/src/main/resources/application.yml`：

```yaml
ai:
  enabled: false
  chat-completions-url:
  api-key:
  model: gpt-4o-mini
  timeout-ms: 10000
  max-context-messages: 20
```

启用外部模型时，将 `enabled` 设为 `true`，并配置 OpenAI-compatible chat completions 地址。例如：

```yaml
ai:
  enabled: true
  chat-completions-url: https://api.example.com/v1/chat/completions
  api-key: your_api_key
  model: your_model
```

## 下一阶段建议

1. 建立消息索引与摘要缓存，避免每次都把完整上下文发给模型。
2. 增加用户/群级 AI 权限开关，明确哪些会话允许 AI 读取上下文。
3. 在移动端 `uniapp` 补齐同等 AI 入口。
4. 增加 AI 消息安全策略：敏感信息脱敏、输出审核、用户反馈。
5. 将社交图、兴趣标签、群活动与 AI 推荐打通。
