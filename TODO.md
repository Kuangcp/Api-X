
# TODO

> 性能专项
- [X] 右侧相应区域的语法高亮问题
- sse响应的性能问题

> Request
request body 功能丰富
- [X] request body json高亮 格式化

- [X] 当一个Request A 在请求中时, 如果点左侧树切换到其他 Request 例如B , 需要A和B 状态独立互不不影响, 也就是说 需要支持多个Request同时执行, 状态和数据需要隔离. 在A和B切换时能看到实时的A和B对应的状态和流输出等, 同样的切换到C 又可以启动C, 当Request在运行中时, 左侧的树Request节点左侧需要加一个loading的动画来标记这个Request是在运行的, 然后执行结束后去掉这个loaing的动态icon. 

日志集成, kotlin-logging + Logback, 在核心的导入导出 初始化数据等环节加日志

- [X] 支持更多参数格式, 表单, 多表单

- [X] 增加快捷键处理 Ctrl Tab 切换Request， 切换时需要弹出一个下拉列表，简洁的展示最近使用的Request，这样就能知道要按几下tab键了，也就是说需要记录最近的Request的打开查看时间，基于这个做倒排， 下拉列表展示Top30 

- [X] Request 加Params Tab 功能和交互和Header是一样的, 但是区别是这里填的内容是 拼接在 URL后的, 例如这里配置了 k1:value1 k2:value2 两行, 在url就是 url?k1=value1&k2=value2

- [X] 1. 多标签页（Multi-Tab）
现有问题是每次只能编辑一个请求，切换时当前编辑状态会丢失。增加 Tab 栏可以同时打开多个请求，类似 Postman / Insomnia 的工作方式。
Tab 可关闭、拖拽排序
每个 Tab 独立维护未保存的编辑状态

2. 代码片段生成（Code Snippet）
将当前请求一键导出为代码，常见场景：前端对接、后端测试、分享给同事。
支持语言：cURL（已有）、Python（requests）、JavaScript（fetch）、Java（OkHttp）、Go、Shell
导出格式可配置（是否包含认证信息、headers 等）

3. 变量自动补全
在 URL、Headers、Body、Params 等任意输入 {{ 时弹出当前环境的变量列表供选择，减少记忆和手误。

4. 快捷键参考面板
当前快捷键不少但没有统一的查看入口。加一个 Ctrl+/ 或 ? 快捷键调出 Cheat Sheet 弹窗。
5. 请求链 / 变量提取
从响应 JSON 中提取值自动设为环境变量。例如：登录接口返回 {"token": "xxx"}，自动提取 {{auth_token}} 供后续请求使用。

7. WebSocket 客户端
长连接调试也是刚需，不过需要全新的连接管理层和 UI。
8. 请求前置/后置脚本
类似 Postman 的 Pre-request Script + Tests，但需要嵌入 JS 引擎（GraalVM），工作量较大。

> 响应区域

- [X] 响应区域 默认展示Raw格式, 考虑性能问题, 需要手动切换到 格式化高亮形式
- [X] 需要加一个 Request 的tab 展示实际发送请求的request内容, 因为左侧现在有变量和勾选的机制, 不知道实际的请求情况
- [X] 需要支持搜索定位


> benchmark 功能

压测 策略还需要再确认 如何设计

响应区域 在 HTTP状态码同一行, 右侧加多一个 benchmark 的icon, 点击后弹窗出来, 窗口和设置页类似, 也是左右分隔, 左侧有这三行: 吞吐量, 稳定数, 稳定时, 然后吞吐量对应的右侧设置是 总量(整数输入框), 稳定数对应的右侧设置是 并发数, 总量, 稳定性对应的右侧设置是 并发数, 持续时间(整数 单位分钟)

执行压测
- 吞吐量 固定请求次数 一次性全部发起请求
- 稳定数 固定并发跑固定请求次数
- 稳定时 固定并发跑一段时间


request目录下的bench目录 存放的是: 压测配置json(参数: 并发数, 总次数, 结果报表: 所有RT值, P50 P75 P90 P99,最小 最大,平均 的RT统计值, QPS, TPS), 包含压测抛出来的的所有har


> 架构与工程
- **缺少自动化测试**：项目完全没有测试（src/test/ 不存在），建议引入 kotlin.test / JUnit5
为核心逻辑（CollectionRepository、EnvironmentSubstitute、CurlParser 等）编写单元测试
- [X] **AppViewModel 过于臃肿**：单一 Composable 持有全部应用状态（tab、session、环境、设置等），建议拆分为多个小
ViewModel（TabViewModel、SessionViewModel、EnvironmentViewModel）
- [X] **无依赖注入**：手动管理所有依赖，建议引入简单 DI（手动构造或 koin）降低耦合
- **无日志框架集成**：TODO.md 提到 `kotlin-logging + Logback` 但 build.gradle.kts 中未配置
- [X] **SQLite 无连接池/WAL 模式**：单连接直连，大数据量下性能瓶颈，建议开启 WAL 模式并复用连接

> 构建与依赖
- [X] **缺少 Kotlin 编译器严格检查**：未开启 `allWarningsAsErrors`、`optIn` 等编译选项

> 性能
- **大响应体内存风险**：响应内容全部在内存中，超大响应（>500MB）可能导致 OOM，建议流式写入临时文件
- [X] **Tree 无虚拟化**：集合/请求量大时 LazyColumn 仍会创建大量节点，建议启用 `key` + 适当分页
- [X] **状态粒度过粗导致不必要的重组**：`AppViewModel` 整体作为单个 `mutableStateOf`，任何字段变化触发全量重组，建议拆分为多个 `mutableStateOf`


- [X] UI/UX
    - **无国际化（i18n）**：所有 UI 字符串硬编码为中文，建议抽取为资源文件，便于后续多语言
    - **无可访问性支持**：缺少 contentDescription、focusable 等属性，屏幕阅读器无法使用
    - **无键盘树导航**：树形列表不支持键盘上下键展开/折叠，仅支持鼠标拖拽
    - **快捷键无统一管理**：快捷键分散在多个文件，建议集中到单一文件中管理

> 代码质量
- [X] **app/ 包过于膨胀**：23 个文件平铺在 app/ 包下，建议按功能拆分子包（app/viewmodel/、app/dialog/、app/settings/
等）
- [X] **业务逻辑与 UI 混合**：多处 Composable 函数中内联业务逻辑（如导入/导出解析），建议抽取到普通类中
- **字符串散落在代码中**：无资源文件或常量类，不利于维护和翻译
- **无输入校验**：URL、Header、Body 等输入在发送前无格式校验，无效请求直接发送

> 可靠性
- [X] **无崩溃恢复机制**：Composable 异常无 ErrorBoundary，整个应用崩溃
- **SQLite 迁移无框架**：当前版本号+手动 SQL 方式，无 Flyway/Liquibase 等迁移框架，协作时易出错
- 忽略 **无请求超时兜底**：HttpClient 未显式设置 connectTimeout / requestTimeout，极端网络下可能永久阻塞