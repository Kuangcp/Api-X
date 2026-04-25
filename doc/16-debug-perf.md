# JDK 21 调试与性能监控

本文介绍桌面应用的调试技巧与性能监控方法。

## 16.1 NativeMemoryTracking 内存追踪

### 启用追踪

```kotlin
jvmArgs += listOf(
    "-XX:NativeMemoryTracking=detail",
)
```

### 命令行查看

```bash
# Windows
jcmd <pid> VM.native_memory summary

# macOS/Linux
jcmd <pid> VM.native_memory summary
```

### 输出示例

```
Native Memory Tracking:
Total: reserved=XXX MB, committed=XXX MB
- Java Heap (reserved=XXX MB, committed=XXX MB)
- Class (reserved=XXX MB, committed=XXX MB)
- Thread (reserved=XXX MB, committed=XXX MB)
- Code Stack (reserved=XXX MB, committed=XXX MB)
- GC (reserved=XXX MB, committed=XXX MB)
```

## 16.2 JFR 飞行记录器

### 启用 JFR

```bash
# 启动时启用
java -XX:StartFlightRecording:settings=default,dumponexit=true,filename=recording.jfr ...

# 动态启用
jcmd <pid> JFR.start delay=5s duration=60s filename=recording.jfr

# 查看记录
jcmd <pid> JFR.view recording.jfr
```

### 内置配置

| 配置 | 说明 |
|------|------|
| `default` | 适合大多数场景 |
| `profile` | 更详细的采样 |
| `continuous` | 持续记录 |

### 常用命令

```bash
# 检查状态
jcmd <pid> JFR.check

# 停止记录
jcmd <pid> JFR.stop

# 转储
jcmd <pid> JFR.dump filename=dump.jfr
```

## 16.3 GC 日志分析

### 启用 GC 日志

```kotlin
jvmArgs += listOf(
    "-Xlog:gc*=info:file=gc.log:time,uptime:filecount=5,filesize=10M",
)
```

### G1 GC 配置

```kotlin
jvmArgs += listOf(
    "-XX:+UseG1GC",
    "-XX:MaxGCPauseMillis=200",
    "-XX:G1HeapRegionSize=4m",
)
```

### 日志分析工具

- **GCViewer** - 可视化 GC 日志
- **GCEasy** - 在线分析
- **IBM Pattern** - 详细模式

### 日志示例

```
[2024-01-01T12:00:00.001+0800] GC(12) Pause Young (Normal) 45M->12M(256M) 23.456ms
```

## 16.4 Skiko 渲染调试

### 渲染 API 配置

```kotlin
jvmArgs += listOf(
    // GPU 合成（推荐）
    "-Dskiko.renderApi=OPENGL",
    
    // 软件渲染（调试）
    "-Dskiko.renderApi=SOFTWARE",
    
    // 快速软件渲染
    "-Dskiko.renderApi=SOFTWARE_FAST",
)
```

### 渲染问题排查

1. **列表滚动不流畅** - 尝试 OPENGL
2. **内存占用高** - 切换 SOFTWARE 测试
3. **显示异常** - 检查 GPU 驱动

> 项目中的配置 (`build.gradle.kts:52`):
```kotlin
"-Dskiko.renderApi=OPENGL",
```

## 16.5 JMC 监控

### Java Mission Control

```bash
# 启动 JMC
jmc &

# 连接到应用
# 自动开启 JFR 录制
```

### 监控指标

- **CPU 占用** - 方法级别分析
- **内存分配** - 对象创建热点
- **GC 分析** - 停顿时间
- **线程** - 死锁检测

## 16.6 Heap Dump 分析

### 获取 Dump

```bash
# 生成 heap dump
jcmd <pid> GC.heap_dump heap.hprof

# JMX 触发
jmap -dump:file=heap.hprof,format=b <pid>
```

### 分析工具

| 工具 | 说明 |
|------|------|
| Eclipse MAT | 免费，功能强大 |
| VisualVM | JDK 内置 |
| YourKit | 商业工具 |

### 常见问题

```
# 内存泄漏
找出持续增长的对象：
- ThreadLocal 引用
- 监听器未注销
- 静态集合积累

# 内存溢出
分析 dump：
- 大对象（数组）
- 类加载器问题
```

## 16.7 火焰图分析

### Async Profiler

```bash
# 采样 CPU
./profiler.sh start -e cpu <pid>
./profiler.sh stop --format=html -o output.html <pid>

# 采样内存分配
./profiler.sh start -e alloc <pid>
```

### 解读火焰图

```
┌─────────────────────────────────────────────┐
│                   main()                    │
│  ┌────────────────────────────────────────┐ │
│  │              process()                 │ │
│  │  ┌──────────────────────────────────┐  │ │
│  │  │           render()               │  │ │
│  │  │  ┌────────────────────────────┐  │  │ │
│  │  │  │        drawText()          │  │  │ │
│  │  │  └────────────────────────────┘  │  │ │
│  │  └──────────────────────────────────┘  │ │
│  └────────────────────────────────────────┘ │
└─────────────────────────────────────────────┘
```

## 16.8 IDE 调试技巧

### IntelliJ IDEA

1. **Compose 检查器**
   - Layout Inspector
   - Recomposition 计数

2. **断点技巧**
   ```
   - 条件断点：url.contains("api")
   - 日志断点：打印变量
   - 方法断点：入/出口
   ```

3. **性能分析**
   ```
   Run > Profiler > CPU Usage
   Run > Profiler > Memory
   ```

### 调试 Compose 重组

```kotlin
// 添加调试标志
System.setProperty("kotlin.compose.compiler.recompose.trace", "true")

// 查看重组日志
```

## 16.9 总结

| 工具/参数 | 用途 |
|---------|------|
| `-XX:NativeMemoryTracking=detail` | 内存追踪 |
| `jcmd JFR.*` | 飞行记录 |
| `-Xlog:gc*=info` | GC 日志 |
| `-Dskiko.renderApi=OPENGL` | 渲染配置 |
| JMC | Mission Control |
| `jcmd GC.heap_dump` | Heap Dump |

**全部博客已完成！**