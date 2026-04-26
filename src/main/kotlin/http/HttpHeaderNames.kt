package http

// https://developer.mozilla.org/zh-CN/docs/Web/HTTP/Reference/Headers
val KNOWN_HTTP_HEADERS: List<String> = listOf(
    // 验证
    "WWW-Authenticate",
    "Authorization",
    "Proxy-Authenticate",
    "Proxy-Authorization",
    // 缓存
    "Age",
    "Cache-Control",
    "Clear-Site-Data",
    "Expires",
    "No-Vary-Search",
    // 条件
    "Last-Modified",
    "ETag",
    "If-Match",
    "If-None-Match",
    "If-Modified-Since",
    "If-Unmodified-Since",
    "Vary",
    // 连接管理
    "Connection",
    "Keep-Alive",
    // 内容协商
    "Accept",
    "Accept-Encoding",
    "Accept-Language",
    "Accept-Patch",
    "Accept-Post",
    // 控制
    "Expect",
    "Max-Forwards",
    // Cookie
    "Cookie",
    "Set-Cookie",
    // CORS
    "Access-Control-Allow-Credentials",
    "Access-Control-Allow-Headers",
    "Access-Control-Allow-Methods",
    "Access-Control-Allow-Origin",
    "Access-Control-Expose-Headers",
    "Access-Control-Max-Age",
    "Access-Control-Request-Headers",
    "Access-Control-Request-Method",
    "Origin",
    "Timing-Allow-Origin",
    // 下载
    "Content-Disposition",
    // 完整性摘要
    "Content-Digest",
    "Repr-Digest",
    "Want-Content-Digest",
    "Want-Repr-Digest",
    // 消息主体信息
    "Content-Length",
    "Content-Type",
    "Content-Encoding",
    "Content-Language",
    "Content-Location",
    // 代理
    "Forwarded",
    "Via",
    // 范围请求
    "Accept-Ranges",
    "Range",
    "If-Range",
    "Content-Range",
    // 重定向
    "Location",
    "Refresh",
    // 请求上下文
    "From",
    "Host",
    "Referer",
    "Referrer-Policy",
    "User-Agent",
    // 响应上下文
    "Allow",
    "Server",
    // 安全
    "Cross-Origin-Embedder-Policy",
    "Cross-Origin-Opener-Policy",
    "Cross-Origin-Resource-Policy",
    "Content-Security-Policy",
    "Content-Security-Policy-Report-Only",
    "Expect-CT",
    "Permissions-Policy",
    "Reporting-Endpoints",
    "Strict-Transport-Security",
    "Upgrade-Insecure-Requests",
    "X-Content-Type-Options",
    "X-Frame-Options",
    "X-Permitted-Cross-Domain-Policies",
    "X-Powered-By",
    "X-XSS-Protection",
    // fetch 元数据请求头
    "Sec-Fetch-Site",
    "Sec-Fetch-Mode",
    "Sec-Fetch-User",
    "Sec-Fetch-Dest",
    "Sec-Purpose",
    "Service-Worker-Navigation-Preload",
    // 服务器发送事件
    "Report-To",
    // 传输编码
    "Transfer-Encoding",
    "TE",
    "Trailer",
    // WebSocket
    "Sec-WebSocket-Accept",
    "Sec-WebSocket-Extensions",
    "Sec-WebSocket-Key",
    "Sec-WebSocket-Protocol",
    "Sec-WebSocket-Version",
    // 其他
    "Alt-Svc",
    "Alt-Used",
    "Date",
    "Link",
    "Retry-After",
    "Server-Timing",
    "Service-Worker",
    "Service-Worker-Allowed",
    "SourceMap",
    "Upgrade",
    "Priority",
    // 实验性 - 归因报告
    "Attribution-Reporting-Eligible",
    "Attribution-Reporting-Register-Source",
    "Attribution-Reporting-Register-Trigger",
    // 实验性 - 客户端提示
    "Accept-CH",
    "Critical-CH",
    // 实验性 - 用户代理客户端提示
    "Sec-CH-UA",
    "Sec-CH-UA-Arch",
    "Sec-CH-UA-Bitness",
    "Sec-CH-UA-Full-Version",
    "Sec-CH-UA-Full-Version-List",
    "Sec-CH-UA-Mobile",
    "Sec-CH-UA-Model",
    "Sec-CH-UA-Platform",
    "Sec-CH-UA-Platform-Version",
    "Sec-CH-UA-WoW64",
    "Sec-CH-Prefers-Color-Scheme",
    "Sec-CH-Prefers-Reduced-Motion",
    "Sec-CH-Prefers-Reduced-Transparency",
    // 实验性 - 设备客户端提示
    "Content-DPR",
    "Device-Memory",
    "DPR",
    "Viewport-Width",
    "Width",
    // 实验性 - 网络客户端提示
    "Downlink",
    "ECT",
    "RTT",
    "Save-Data",
    // 实验性 - 隐私
    "DNT",
    "Tk",
    "Sec-GPC",
    // 实验性 - 安全
    "Origin-Agent-Cluster",
    // 实验性 - 服务器发送事件
    "NEL",
    // 实验性 - 主题 API
    "Observe-Browsing-Topics",
    "Sec-Browsing-Topics",
    // 实验性 - 其他
    "Accept-Signature",
    "Early-Data",
    "Set-Login",
    "Signed-Headers",
    "Speculation-Rules",
    "Supports-Loading-Mode",
    // 非标准
    "X-Forwarded-For",
    "X-Forwarded-Host",
    "X-Forwarded-Proto",
    "X-DNS-Prefetch-Control",
    "X-Robots-Tag",
)

fun filterHeaders(query: String): List<String> {
    if (query.isBlank()) return emptyList()
    val q = query.lowercase()
    return KNOWN_HTTP_HEADERS
        .map { header -> header to (if (header.lowercase() == q) 0 else if (header.lowercase().startsWith(q)) 1 else if (header.lowercase().contains(q)) 2 else 3) }
        .filter { it.second < 3 }
        .sortedBy { it.second }
        .take(8)
        .map { it.first }
}