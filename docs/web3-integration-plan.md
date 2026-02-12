# Yeying Social Web3 能力融合方案（身份 / 社交图可携带 / 经济激励）

本文档基于开源 Yeying Social 工程进行分析与改造规划，目标是在保留现有 IM 稳定性的基础上，引入 Web3 身份、社交图可携带与经济激励三大核心能力。所有建议均以可落地、可渐进演进为原则。

## 1. 现有项目现状与改造切入点

### 1.1 模块与职责
- `platform`：业务平台服务（HTTP API），处理注册登录、好友/群组/消息等业务逻辑。
- `server`：消息推送服务（Netty WebSocket），负责实时消息转发与在线状态维护。
- `rtc`：RTC 信令与通话编排服务（独立服务）。
- `web3-identity`：Web3 身份认证服务（SIWE/UCAN）。
- `web3-graph`：Web3 社交图服务（关注/导入导出/同步）。
- `web3-incentive`：Web3 激励服务（积分/激励策略）。
- `message-connector`：消息推送连接 SDK（服务端使用）。
- `client`：协议模型 SDK（对外发布，仅含协议模型）。
- `mq-api`：MQ 抽象接口。
- `mq-redis`：Redis MQ 实现。
- `common`：公共工具包（JWT、常量、通用工具等）。
- `web`：Web 端（Vue）。
- `uniapp`：移动端/小程序（uniapp）。

#### 1.1.1 模块划分检查结论（已落地）
- `client` 已收敛为协议模型包，避免 SDK 依赖过重。
- `message-connector` 作为服务端连接 SDK，避免与协议模型耦合。
- `mq-api` + `mq-redis` 分离，实现长期 Redis 方案同时保留抽象层。
- `rtc` 已拆分为独立服务，符合实时通话的高可用/扩展需求。
- Web3 能力拆分为 `web3-identity` / `web3-graph` / `web3-incentive`，便于独立演进与权限控制。
- 结论：模块边界清晰，后续可按需拆分 `common` 中与认证/响应封装相关的通用能力，减少重复代码。

### 1.2 当前身份与会话机制
- 账号登录接口：`/platform/src/main/java/com/bx/implatform/controller/LoginController.java`。
- Web3 登录接口：`/web3-identity` 的 `Web3PublicAuthController`（`/api/v1/public/auth/challenge`、`/verify`、`/refresh`）。
- 登录逻辑：账号体系由 `UserServiceImpl#login` 签发 accessToken/refreshToken（JWT），Web3 登录由 `web3-identity` 统一签发 JWT。
- WebSocket 登录：`server` 在 `LoginProcessor` 内校验 accessToken 后建立连接（与 `web3-identity` 签发的 JWT 兼容）。
- 数据表：`t_user` 存储用户基本资料、钱包字段与封禁标识等（见 Flyway 迁移 `common/src/main/resources/db/migration/V1__init.sql`）。

结论：现有体系以“传统账号体系 + JWT 访问令牌”构建。Web3 身份改造可以以“兼容叠加”的方式切入（保留原账号能力，同时引入钱包/DID 登录与绑定）。

### 1.3 当前社交图结构
- 好友关系：`t_friend`，双向好友模型。
- 群组关系：`t_group` 与 `t_group_member`。

结论：当前是“强关系+群组”模型，需要扩展“单向关注/订阅”与“可导出/可迁移”能力。

### 1.4 当前消息与内容结构
- 私聊/群聊消息表：`t_private_message` 与 `t_group_message`。
- 消息内容存储为文本与附件引用（文件在 `t_file_info` 管理）。

结论：IM 消息以私密、实时为主，Web3 要求的“内容确权/可验证”可以通过“链上锚定 + 链下存储”的方式引入，不影响实时通讯能力。

## 2. Web3 三大能力设计

### 2.1 Web3 身份（Identity）
目标：支持“钱包登录/绑定 + DID 解析 + 多终端统一身份”。

建议路径：
1) 在原有账号体系上叠加“钱包身份”，保持用户可选择账号登录或钱包登录。
2) 使用 SIWE（EIP-4361）完成签名登录；生成与现有 JWT 体系兼容的 accessToken。
3) 引入 DID（去中心化标识）作为跨应用身份锚点，支持多钱包绑定到同一 DID。

关键能力：
- 钱包登录：签名挑战（nonce）-> 校验签名 -> 发放平台 token。
- 钱包绑定/解绑：账号已登录的情况下绑定钱包地址与公钥。
- DID 解析：可选使用 W3C DID 作为用户“可携带身份”标识，绑定后支持导出与外部系统对接。

### 2.2 社交图可携带（Social Graph Portability）
目标：允许用户导出/导入关注关系、好友关系、群组关系，并与外部协议同步。

建议路径：
1) 引入“单向关注”模型（follow），与现有双向好友模型并存。
2) 提供标准化导出格式（JSON/CSV），包含 DID、钱包地址、用户标识、关注关系。
3) 提供可选的“外部协议同步器”（如 Lens / Farcaster / Nostr 等），实现双向映射。

关键能力：
- Graph Export/Import API。
- Graph Sync Service：对外部协议进行同步与冲突处理。
- Graph Privacy：允许用户选择导出范围（公开/私密/仅关注）。

### 2.3 经济激励（Incentives）
目标：围绕社交行为构建可持续的激励体系。

建议路径：
1) 从“积分/徽章体系”起步，采用链下账本，降低链上成本。
2) 引入打赏/小额支付（tip）和群组 Token Gate（NFT/Token 持有门槛）。
3) 后续根据生态发展选择是否上链（例如积分兑换为链上代币或 NFT）。

关键能力：
- 行为积分：发帖、被点赞、分享、邀请等行为产出积分。
- 打赏与付费私信：可选链上支付（需处理 Gas 与体验）。
- Token/NFT Gate：群组与频道可设置资产门槛。

### 2.4 链与身份策略定稿（本次执行）
- 链选择：优先 EVM 体系，后续再扩展多链。
- 登录标准：SIWE + UCAN（UCAN 优先，SIWE 作为 Root Proof 与兼容登录）。
- 账户策略：钱包未绑定时默认自动注册（可配置关闭）。
- DID 策略：默认关闭，启用后使用 `did:pkh:eip155:<chainId>:<address>`。
- 域名校验：默认不强制（可通过配置开启）。

## 3. 技术架构建议（增量扩展）

### 3.1 服务分层
已拆分/规划的服务边界如下：
- `rtc`：独立 RTC 服务，负责通话信令与通话编排。
- `web3-identity`：SIWE 登录、挑战登录、UCAN 校验、钱包绑定、DID 解析。
- `web3-graph`：关注关系维护、导入导出、协议映射（当前为占位服务）。
- `web3-incentive`：积分规则、奖励结算、打赏/付费逻辑（当前为占位服务）。
- Chain Indexer（可选）：监听链上资产变更，提供 Token/NFT 校验。

### 3.2 数据流（示意）
1) 登录：
   - 用户发起钱包登录 -> 获取 nonce -> 签名 -> 平台校验 -> 发放 JWT accessToken（与现有系统一致）。
2) 社交图：
   - 用户关注/取关 -> 写入本地 Graph 表 -> 可选同步到外部协议。
3) 激励：
   - 用户行为触发 -> Incentive Engine 结算 -> 记录到积分账本。
   - Token/NFT 校验 -> 通过 Chain Indexer 或 RPC 读链。

## 4. 数据模型与接口改造

### 4.1 数据表新增与扩展（建议）

在 `platform` 数据库新增或扩展：

已落地（本次代码已实现）：
1) `t_user` 新增字段：
   - `did`：DID 标识（可空）。
   - `wallet_address`：默认钱包地址（可空）。
   - `wallet_type`：链类型（EVM）。
   - `wallet_verified_at`：钱包绑定时间。
2) 新表 `t_wallet`：支持多钱包绑定
   - `id`
   - `user_id`
   - `address`
   - `chain_type`
   - `public_key`
   - `is_primary`
   - `created_time`

待落地（后续迭代）：
1) 新表 `t_follow`：单向关注关系
- `id`
- `follower_id`
- `followee_id`
- `created_time`

2) 新表 `t_identity_claim`：身份凭证与声明
- `id`
- `user_id`
- `clat_type`（VC/社交证明/链上证明）
- `clat_payload`
- `created_time`

3) 新表 `t_reward_ledger`：积分/奖励流水
- `id`
- `user_id`
- `action_type`
- `amount`
- `ref_id`
- `created_time`

4) 群组 Token Gate：
- 在 `t_group` 增加 `gate_type`、`gate_contract`、`gate_min_balance` 等字段。

### 4.2 API 设计（建议）

身份相关：
- 公共登录（`web3-identity`）：
  - `POST /api/v1/public/auth/challenge`
  - `POST /api/v1/public/auth/verify`
  - `POST /api/v1/public/auth/refresh`
  - `GET  /api/v1/public/auth/profile`（支持 UCAN）
- 绑定与 SIWE（`web3-identity`）：
  - `POST /auth/siwe/nonce`
  - `POST /auth/siwe/verify`
  - `POST /auth/wallet/link`
  - `POST /auth/wallet/unlink`
> 以上接口已在 `web3-identity` 中落地，前端使用 `web3-bs` 发起 challenge 登录并优先使用 UCAN 调用。

社交图相关：
- `POST /graph/follow`
- `POST /graph/unfollow`
- `GET /graph/export`
- `POST /graph/import`
- `POST /graph/sync`

经济激励相关：
- `GET /rewards/balance`
- `POST /rewards/claim`
- `POST /tips/send`
- `POST /groups/{id}/gate/check`

### 4.3 JWT 载荷扩展
在 `JwtUtil` 生成的 session 里增加：
- `did`
- `primaryWallet`
- `identityLevel`（未绑定/已绑定/验证通过）

本次未变更 JWT 载荷，保持 `server` 的 WebSocket 登录逻辑不变；后续可按需增强。

## 5. 前端与客户端改造

### 5.1 Web 端（web）
- 新增钱包登录入口（与原账号登录并列），接入 `web3-bs`。
- 默认走 Challenge 登录获取 JWT，同时生成 UCAN Root/Invocation（优先 UCAN）。
- 个人资料页展示 DID、钱包地址、链上徽章。
- 关注/粉丝页面。
- Token Gate 群组入口提示。

### 5.2 移动端（uniapp）
- 引入钱包连接方案（根据端环境选择）。
- 提供扫码绑定或深度链接钱包签名。

## 6. 安全与治理要点

- 防女巫：钱包多开/恶意刷分需结合设备指纹与行为模型限制。
- 风控与合规：打赏/付费功能需关注支付合规与资金流。
- 私密消息：对私聊内容“仅锚定哈希、不上链明文”。

## 7. 分阶段路线图（建议）

### Phase 0：基础清理（1-2 周）
- 梳理现有登录/用户体系。
- 预留数据库字段与接口。

### Phase 1：钱包登录与绑定（2-4 周）
- Challenge 登录（web3-identity） + UCAN Root/Invocation。
- SIWE 登录与钱包绑定/解绑。
- JWT 载荷扩展。

### Phase 2：社交图可携带（3-5 周）
- 关注模型。
- 导入/导出 API。
- 基础同步器（先本地格式）。

### Phase 3：经济激励（3-6 周）
- 积分系统与奖励规则。
- 打赏/付费私信。

### Phase 4：外部协议生态对接（持续）
- 视需求接入 Lens / Farcaster / Nostr 等。
- 引入链上数据索引服务。

## 8. 本次代码落地摘要（已完成）

### 8.1 后端接口
- `web3-identity` 公共登录：
  - `POST /api/v1/public/auth/challenge`
  - `POST /api/v1/public/auth/verify`
  - `POST /api/v1/public/auth/refresh`
  - `GET  /api/v1/public/auth/profile`（支持 UCAN）
- `web3-identity` SIWE 与绑定接口：
  - `POST /auth/siwe/nonce`、`POST /auth/siwe/verify`
  - `POST /auth/wallet/link`、`POST /auth/wallet/unlink`
- `rtc` 独立服务：承接 `/webrtc/**` 与 `/system/config`

### 8.2 数据库
- `t_user` 新增字段：`did`、`wallet_address`、`wallet_type`、`wallet_verified_at`。
- 新增表：`t_wallet`（多钱包绑定）。
> 现有库需要执行对应 `ALTER TABLE` 与建表语句。

### 8.3 配置项（web3-identity）
```yaml
web3:
  auth:
    nonceExpireIn: 300
    autoRegister: true
    defaultChainId: 1
    expectedDomain: "social.yeying.pub"
  did:
    enabled: false
  ucan:
    audience: "did:web:social.yeying.pub"
    resource: "profile"
    action: "read"
```

### 8.4 前端对接
- Web 端接入 `web3-bs`，提供钱包登录入口（UCAN 优先）。
- 新增 `VUE_APP_RTC_BASE_API` / `VUE_APP_WEB3_BASE_API` 环境变量，分别指向 RTC 与 Web3 身份服务。

## 9. 关键决策与待确认事项

- 目标链选择（EVM/L2/其他）。
- 是否需要 DID（或仅钱包地址即可）。
- 激励是否上链（上链成本与体验权衡）。
- 数据可携带的范围（公开 vs 私密）。

## 10. 参考资料（外部协议与标准）

- EIP-4361: Sign-In with Ethereum (SIWE): https://eips.ethereum.org/EIPS/eip-4361
- W3C DID Core: https://www.w3.org/TR/did-core/
- Lens Protocol Docs: https://lens.xyz/docs
- Farcaster Protocol Docs: https://docs.farcaster.xyz
- Nostr NIP-01: https://nostr-nips.com/nip-01
