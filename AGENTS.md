# 框架背景
JDK21 + Compose1.10.3 + Kotlin 2.3.20

# 构建规则
- 必须使用 gradle 不能使用gradlew.
- 做 编译检查 时 只能执行 `gradle compileKotlin` 命令， 而且 必须手动指定JDK21的目录， 最终命令如下：
    - JAVA_HOME=~/.sdkman/candidates/java/21.0.10-oracle PATH="$JAVA_HOME/bin:$PATH" gradle compileKotlin 2>&1

# UI组件规则
- 添加新的组件时都需要考虑 Dark和Light的主题配色问题, 主要是字体的颜色.
