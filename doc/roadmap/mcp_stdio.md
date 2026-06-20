# MCP stdio 调试设计 Roadmap

## 目标

把当前 MCP SSE 调试器的会话型体验扩展到 stdio transport，使本地命令型 MCP Server 也能按同一套交互模型调试：

- `Connect` 启动并保持一个 MCP 子进程。
- 初始化后拉取 `tools/list`、`resources/list`、`prompts/list`。
- 左侧 catalog 点击只切换 tool / resource / prompt 上下文，不重启进程。
- 中间参数表单和 JSON 草稿继续复用磁盘缓存。
- 右侧继续展示统一协议日志、响应、通知和 stderr。
- 用户显式 `Disconnect` 时才关闭子进程。

## 当前状态

项目里已经有两类 stdio 能力：

- `McpStdioDebugger.kt`：旧的一次性调试入口，适合 `Run -> 启动进程 -> 初始化 -> 调一次 -> 断开`。
- `McpLiveConnection.kt` 里的 `StdioLiveConnection`：已经有常驻连接骨架，支持启动进程、stdin 写 JSON-RPC、stdout 读取响应、stderr 收集、连续 call。

但 stdio 还没有像 SSE 一样完成专项打磨。当前更准确的状态是：

- SSE：已进入可日常调试状态。
- stdio：底层能力已有，但需要专门验证 UI 行为、进程生命周期、错误恢复和日志展示。

## 设计原则

### 1. 与 SSE 共用上层会话模型

stdio 不应该另做一套 UI。上层仍复用：

- `McpConnectionState`
- `McpSelectionState`
- `McpDraftStore`
- `McpCatalogStore`
- `McpSessionLogStore`
- `McpProtocolLogView`

transport 差异只留在 `McpLiveConnection` 实现层。

### 2. 进程生命周期必须显式

stdio 的连接不是 HTTP 长连接，而是一个真实子进程。UI 上需要让用户清楚知道：

- 当前是否已启动进程。
- 启动命令是什么。
- 进程是否异常退出。
- stderr 是否有输出。
- Disconnect 是否已经杀掉或等待退出。

### 3. stdout 只当 MCP 协议通道

stdio MCP 的 stdout 应只输出 JSON-RPC 消息。如果 server 把普通日志写到 stdout，会污染协议流。

设计上应：

- stdout 逐行读取 JSON-RPC。
- 非 JSON 行作为协议错误记录到日志。
- stderr 独立展示为 server 日志。
- 不把 stderr 混进 JSON-RPC Messages。

### 4. 不自动重启

第一版不要做进程异常退出后的自动重启，避免隐藏真实问题。

推荐行为：

- 进程退出后标记连接断开。
- 右侧日志提示 exit code。
- Send 按钮提示需要重新 Connect。
- 用户手动 Reconnect。

## 第一阶段：stdio 常驻连接验证

目标是确认 `StdioLiveConnection` 能稳定跑完最小闭环。

### 范围

- 使用非 HTTP URL 判断为 stdio 命令。
- `Connect` 启动进程。
- 发送 `initialize`。
- 发送 `notifications/initialized`。
- 拉取 catalog。
- 连续发送多次 `tools/call`。
- `Disconnect` 关闭 stdin 并结束进程。

### 需要补强

- 连接成功后记录进程启动命令。
- 连接失败时展示完整异常。
- 子进程提前退出时标记 `isConnected = false`。
- `call` 时如果进程已退出，给出明确错误。
- Disconnect 后清理 writer、process、reader thread。

### 验证用例

- 启动一个简单 stdio MCP server。
- Connect 后能看到 tools/resources/prompts。
- 连续调用同一个 tool 两次，确认没有重启进程。
- 切换 tool 后草稿保留。
- Disconnect 后进程消失。
- Disconnect 后再次 Send 不应复用旧进程。

## 第二阶段：日志与错误体验

目标是让 stdio 调试时能看清 server 到底发生了什么。

### 协议日志

Messages 里展示：

- `req initialize`
- `resp initialize`
- `req tools/list`
- `resp tools/list`
- `req tools/call`
- `resp tools/call`

Notifications 里展示：

- client 发出的 `notifications/initialized`
- server 发来的 `notifications/*`

Raw 里保留原始 stdout JSON-RPC 行。

### stderr 展示

stderr 应作为独立日志进入右侧输出，不参与 JSON-RPC 解析。

建议格式：

```text
[MCP stderr]
+...
```

后续如果 UI 空间允许，可以增加 `stderr` filter 或 tab。

### 异常场景

需要明确展示：

- 命令为空。
- 命令不存在。
- 参数拆分失败。
- 进程启动失败。
- stdout 非 JSON。
- 请求超时。
- 进程提前退出。
- stdin 写入失败。

## 第三阶段：命令配置体验

stdio server 通常需要命令、参数和环境变量。当前可以先沿用 URL/Env 输入区，但后续建议改成更明确的 stdio 配置。

### 推荐配置模型

```kotlin
data class McpStdioTarget(
    val command: String,
    val args: List<String>,
    val env: List<Pair<String, String>>,
    val workingDirectory: String? = null,
)
```

### UI 方向

- transport 选择：`SSE` / `stdio`。
- stdio 下展示：
  - Command
  - Args
  - Working directory
  - Env
- SSE 下展示：
  - URL
  - Headers

这样能避免“URL 输入框里填命令”的语义混乱。

### 命令解析

现有 `splitLiveCommandLine` 可以先用，但长期看建议支持：

- Windows 路径空格。
- 引号参数。
- 环境变量展开。
- working directory。

## 第四阶段：catalog 与缓存策略

stdio 的 catalog 缓存仍然放在 Request 目录下：

```text
request/<requestId>/mcp/catalog.json
request/<requestId>/mcp/session.log
request/<requestId>/mcp/drafts/
```

### 刷新策略

- Connect 后自动刷新 catalog。
- 点击 Refresh Catalog 复用当前 stdio 进程发送 list 请求。
- 收到 `notifications/tools/list_changed` 等通知后自动刷新。
- 如果进程断开，Refresh Catalog 应提示先 Connect，或走一次性 fallback。

### 缓存读取

- 重启应用后优先展示磁盘 catalog。
- Connect 成功后用最新 catalog 覆盖磁盘。
- catalog 解析失败时保留旧缓存，不清空左侧树。

## 第五阶段：高级 MCP 能力

这部分不影响第一版 stdio 调试，但后续要成为完整 MCP 调试台时需要考虑。

### Roots

如果 server 请求 roots 能力，需要支持：

- 配置 roots 列表。
- 响应 `roots/list`。
- 展示 server 请求 roots 的日志。

### Sampling

如果 server 发起 sampling 请求，需要 UI 能展示并决定：

- 允许。
- 拒绝。
- 使用哪个模型。

第一版可以先明确“不支持 sampling”，收到请求时返回 method not found 或 unsupported。

### Elicitation

如果 server 请求用户补充输入，需要弹窗或右侧交互区域。

第一版同样可以先记录并返回 unsupported。

## 实现顺序建议

1. **验证现有 `StdioLiveConnection`**：不改 UI 大结构，只跑通常驻进程、连续调用和断开。
2. **补进程状态**：退出码、stderr、提前退出、写入失败都要能反馈到 `McpConnectionState` 和右侧日志。
3. **补 stdio 配置 UI**：把命令/参数/env 从 URL 语义里拆出来。
4. **统一协议视图**：确认 Messages / Notifications / Raw 对 stdio 和 SSE 输出一致。
5. **补高级能力兜底**：roots/sampling/elicitation 先做 unsupported 日志与响应，再按真实场景扩展。

## 风险点

- Windows 命令行拆分容易出坑，尤其是带空格路径和引号参数。
- 某些 server 会把日志写 stdout，导致协议解析失败。
- 子进程退出和 UI 状态不同步，会造成“看似 connected，实际 stdin 已断”。
- stderr 如果不及时 flush，用户会误以为 server 卡死。
- 自动重连可能掩盖 server 崩溃原因，第一版不建议做。

## 验收标准

stdio 调试可认为进入可用状态，需要满足：

- 同一个 stdio server 进程内能连续调用多个 tool。
- 切换 tool/prompt/resource 不重启进程。
- 参数表单、JSON 草稿、磁盘缓存都能正常工作。
- 进程退出、超时、stderr、协议错误都有可见日志。
- Disconnect 后进程确实关闭。
- Reconnect 后能重新初始化并刷新 catalog。
