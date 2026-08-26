# 二进制响应处理 设计文档

> 日期：2026-08-26
> 目标：右侧响应区对「二进制文件」响应做专门处理——不展示乱码正文，改为一张卡片，提供「保存到文件」「用系统默认程序打开」两个入口。

## 背景

当前 `sendRequestStreaming`（`http/HttpStreaming.kt`）对响应正文一律以 UTF-8 文本流式读取（`InputStreamReader` + `onChunk`），二进制响应会被当作文本渲染成乱码（mojibake）。本设计在读取正文前判定二进制，二进制的正文不转文本、直接落盘到临时文件，UI 展示卡片。

## 1. 二进制判定

在 `sendRequestStreaming` 收到响应头后、读取正文前判定，顺序如下：

1. **Content-Type 命中二进制类型** → 判定为二进制。
2. 否则用与现有 `sniffSseContent` 相同的 mark/reset 方式，在前 N 字节嗅探 NUL 字节等二进制特征 → 兜底。

SSE 判定优先：SSE 响应（`text/event-stream` 或正文嗅探为 `data:`/`event:`）不算二进制。

新增辅助函数 `isBinaryContentType(mime: String): Boolean`：命中 `image/`、`audio/`、`video/`、`font/`、`application/octet-stream`、`application/pdf`、`application/zip`、`application/gzip`、`application/x-gzip`、`application/x-tar`、`application/x-7z-compressed`、`application/x-rar-compressed`、`application/x-bzip2` 等返回 true；含 `json` / `xml` / `javascript` / `x-www-form-urlencoded` / `text` 的返回 false。

## 2. 原始字节落盘

判定为二进制后：

- 正文不转文本，使用解压后的流（仍走 `wrapResponseBodyStream`，保证 gzip 等 Content-Encoding 正确解压）直接写入临时文件 `<responseDir>/<epochMs>.bin`（与 HAR 同目录、同 stem）。
- 按块累加 `control.totalBytes` 并回调 `onProgress`；**不写入任何 `responseLines`**。
- `RequestControl` 增加字段：`responseWasBinary`、`binaryTempFile: Path?`、`binaryFileName: String`、`binaryContentType: String`。
- 新增回调 `onBinaryDetected: (BinaryResponseInfo) -> Unit`。

## 3. 数据模型

新增 `@Serializable data class BinaryResponseInfo(fileName: String, contentType: String, tempFilePath: String, sizeBytes: Long)`：

- `HarSnapshot` 增加 `binaryInfo: BinaryResponseInfo?`；`HarLogCodec` 将其写入/读取 `_apiX.binary` 扩展字段。**只存路径与元信息，不 base64 正文**。
- `CachedHttpResponse` 增加同名字段（默认 `null`，兼容旧 HAR）。
- `RequestSession` 增加 `binaryInfo by mutableStateOf<BinaryResponseInfo?>(null)`。

## 4. 保存 / 打开交互（UI）

`ResponsePanel` / `ResponseBodyView` 中，当 `binaryInfo != null` 时，正文区替换为居中卡片：

- 图标 + 「二进制响应」 + 文件名 + 大小 + Content-Type。
- 「保存到文件」按钮 → `java.awt.FileDialog(..., SAVE)`（复用 `AppActions.kt:375` 的导出模式），预填文件名 → `Files.copy` 临时文件到目标路径 → Toast 成功；临时文件不存在时弹错误提示。
- 「打开」按钮（用系统默认程序打开）→ `java.awt.Desktop.getDesktop().open(File)`；临时文件不存在或 `Desktop` 不支持时弹错误提示。
- 文件名提示来源优先级：`Content-Disposition` 的 `filename=` → URL 末段 → MIME 扩展名映射 → `response.bin`。
- 复制正文按钮对二进制自动禁用（`responseLines` 为空时现有 `copyResponseBodyEnabled` 逻辑已禁用）。

## 5. 接线

- `startRequest`（`AppOperations.kt`）：reset 时清 `binaryInfo`；`onBinaryDetected` 中设置 session；保存 HAR 时携带 `control` 上的二进制信息。
- `loadHistory`（`AppActions.kt`）：从 `CachedHttpResponse.binaryInfo` 恢复 session 状态。
- `Main.kt`：给 `ResponsePanel` 传 `binaryInfo` 与 `onSaveBinaryResponse` / `onOpenBinaryResponse`（落到 AppActions，带 ToastState）。

## 6. 临时文件清理

- `.bin` 位于 `responseDir/<epochMs>.bin`（与 HAR 同 stem）。
- `clearResponseAndBenchLogs`（删除响应与压测日志）与 `deleteRequestArtifacts`（删除请求）已递归清空整个目录 → 自动覆盖 `.bin`。
- `pruneOld`（每请求最多 10 条历史）需扩展：删除过期 HAR 时一并删除同 stem 的 `.bin`，避免孤儿文件。

## 7. 边界与取舍

- 历史记录中的二进制：临时文件被清理后，保存/打开按钮需优雅降级为错误提示（这是「仅记录路径、不 base64」方案的已知取舍）。
- 二进制正文不进入 `responseLines`，因此 `control.snapshotRawBodyLines()` 对二进制返回空，HAR 的 `content.text` 为空（二进制信息由 `_apiX.binary` 承载）。

## 8. 测试 / 验证

- 无单元测试框架，验证手段为 `gradle compileKotlin` 通过 + 运行 app 人工核对：
  - 请求一个图片/PDF/zip 接口 → 显示二进制卡片（非乱码），大小/时间/状态码正确。
  - 点「保存到文件」→ 弹出系统保存对话框，保存后文件内容与原始响应一致。
  - 点「打开」→ 系统默认程序打开该文件。
  - 切历史记录再点保存/打开 → 能正常导出（临时文件仍在）；删除日志后再点 → 优雅报错。
  - 删除请求/删除响应日志 → `.bin` 一并清理，无孤儿文件。
