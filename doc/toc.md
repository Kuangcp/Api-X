# Api-X 技术博客大纲

## 目标读者
- 普通 Java 开发者
- Kotlin 初学者（有一定 Java 基础）

---

## 第一章：Kotlin 入门与桌面开发基础

### 1.1 Kotlin 语言基础
- **博客 1**: [从 Java 到 Kotlin：语法快速上手](./01-basic-kotlin.md)
  - var/val 声明、智能类型推断
  - data class 数据类与解构
  - 扩展函数与属性
  - lambda 表达式与高阶函数
  - null 安全（?. / ?: / !!）

### 1.2 Compose Desktop 入门
- **博客 2**: [Compose Desktop 初体验：构建第一个桌面应用](./02-compose-desktop.md)
  - Kotlin Multiplatform 与 Compose 简介
  - compose.desktop 依赖配置
  - @Composable 函数与状态管理
  - remember / mutableStateOf 状态更新原理

- **博客 3**: [Compose 布局基础与 Material Design](./03-compose-layout.md)
  - Row / Column / Box 布局
  - LazyColumn 高效列表渲染
  - Material3 主题切换
  - 修饰符（Modifier）链式调用

---

## 第二章：项目核心功能实现

### 2.1 HTTP 请求处理
- **博客 4**: [Java HttpURLConnection 到 Kotlin 协程](./04-http-kt.md)
  - JDK 21 HttpClient API
  - 协程与 suspend 函数
  - 异步请求与.flow
  - 响应流式处理（Streaming）

- **博客 5**: [请求面板与响应展示实现](./05-request-response.md)
  - 状态驱动的 UI 更新
  - JSON 语法高亮实现
  - Form 表单数据处理
  - 请求历史存储

### 2.2 数据持久化
- **博客 6**: [SQLite 在 Kotlin 中的使用](./06-sqlite-kt.md)
  - JDBC 连接 SQLite
  - SELECT/INSERT/UPDATE/DELETE 操作
  - 协程中的数据库操作

- **博客 7**: [序列化与 JSON 处理](./07-serialization.md)
  - kotlinx.serialization 入门
  - JSON 解析与生成
  - data class 与序列化映射
  - Postman 格式导入导出

### 2.3 环境变量与动态替换
- **博客 8**: [环境变量系统设计](./08-environment.md)
  - 环境切换机制
  - 变量替换解析器
  - Auth 继承与处理

---

## 第三章：UI 交互与用户体验

### 3.1 高级 UI 组件
- **博客 9**: [Compose 树形组件与侧边栏](./09-tree-sidebar.md)
  - LazyColumn 实现多级树
  - 展开/收起状态管理
  - 拖拽与缩放处理

- **博客 10**: [对话框与全局搜索](./10-dialogs-overlay.md)
  - ModalBottomSheet 底部弹出
  - 对话框状态管理
  - Ctrl+K 全局搜索实现
  - RecentRequest 快速切换

### 3.2 主题与样式
- **博客 11**: [Compose 主题系统与动态配色](./11-theming.md)
  - Material3 颜色体系
  - 深色/浅色主题切换
  - 自定义主题配置
  - Hex 颜色解析

### 3.3 快捷键系统
- **博客 12**: [桌面应用快捷键绑定](./12-shortcuts.md)
  - KeyEvent 监听
  - 全局快捷键冲突解决
  - RecentRequest 切换overlay

---

## 第四章：工程实践

### 4.1 项目架构
- **博客 13**: [Kotlin 桌面应用架构设计](./13-architecture.md)
  - MVVM 模式在 Compose 中应用
  - Repository 数据层抽象
  - 模块分包策略（app/db/http/tree）
  - 依赖管理

### 4.2 构建与打包
- **博客 14**: [Gradle Kotlin DSL 与打包配置](./14-gradle-build.md)
  - build.gradle.kts 基础配置
  - Compose 桌面应用打包
  - JVM 参数调优
  - 多平台安装包生成（MSI/DMG/Deb）

### 4.3 数据同步与导入导出
- **博客 15**: [Postman 数据格式兼容](./15-postman-sync.md)
  - Postman Collection V2.1 格式
  - JSON 导出与版本管理
  - 数据目录同步机制
  - Git 版本化管理数据

### 4.4 调试与监控
- **博客 16**: [JDK 21 调试与性能监控](./16-debug-perf.md)
  - JFR 飞行记录器
  - NativeMemoryTracking 内存追踪
  - GC 日志分析
  - Skiko 渲染调试

---

## 附录

### A. 博客关联的项目文件索引
### B. 核心类与函数速查表
### C. 推荐学习资源