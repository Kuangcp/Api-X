# Compose 主题系统与动态配色

本文介绍 Material3 主题、动态配色与自定义背景。

## 11.1 Material3 主题基础

### 颜色方案

```kotlin
val darkColorScheme = darkColorScheme(
    primary = Color(0xFFBB86FC),
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF4A148C),
    secondary = Color(0xFF03DAC6),
    onSecondary = Color.Black,
    background = Color(0xFF121212),
    onBackground = Color.White,
    surface = Color(0xFF1E1E1E),
    onSurface = Color.White,
)

val lightColorScheme = lightColorScheme(
    primary = Color(0xFF6200EE),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8DEF8),
    secondary = Color(0xFF03DAC6),
    onSecondary = Color.Black,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
)
```

### 使用主题

```kotlin
@Composable
fun AppContent() {
    var isDarkTheme by remember { mutableStateOf(true) }
    
    MaterialTheme(
        colors = if (isDarkTheme) darkColors() else lightColors()
    ) {
        Column(
            modifier = Modifier.background(MaterialTheme.colors.background)
        ) {
            Text(
                "Hello",
                color = MaterialTheme.colors.onBackground
            )
        }
    }
}
```

## 11.2 深色/浅色主题切换

### 状态管理

```kotlin
var isDarkTheme by remember { mutableStateOf(true) }

MaterialTheme(
    colors = if (isDarkTheme) darkColors() else lightColors()
) {
    // 界面内容
}
```

### 切换按钮

```kotlin
IconButton(onClick = { isDarkTheme = !isDarkTheme }) {
    Icon(
        imageVector = if (isDarkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
        contentDescription = if (isDarkTheme) "浅色模式" else "深色模式"
    )
}
```

> 项目中的主题切换 (`src/main/kotlin/app/Main.kt:293-297`):
```kotlin
var isDarkTheme by remember { mutableStateOf(true) }

MaterialTheme(
    colors = if (isDarkTheme) darkColors() else lightColors()
) {
    // ...
}
```

## 11.3 自定义背景颜色

### Hex 颜色解析

```kotlin
fun parseHexColorOrNull(input: String): Color? {
    val value = input.trim().removePrefix("#").trim()
    if (value.isEmpty()) return null
    
    val argb = when (value.length) {
        6 -> "FF$value"
        8 -> value
        3 -> {
            val expanded = value.map { "$it$it" }.joinToString("")
            "FF$expanded"
        }
        else -> return null
    }
    
    return Color(
        red = argb.substring(2, 4).toInt(16) / 255f,
        green = argb.substring(4, 6).toInt(16) / 255f,
        blue = argb.substring(6, 8).toInt(16) / 255f,
        alpha = argb.substring(0, 2).toInt(16) / 255f,
    )
}
```

> 项目中的实现 (`src/main/kotlin/app/UiColorHex.kt:7-33`):
```kotlin
fun parseHexColorOrNull(input: String): Color? {
    val value = input.trim().removePrefix("#").trim()
    if (value.isEmpty()) return null
    val argb = when (value.length) {
        6 -> {
            if (!value.all { it.digitToIntOrNull(16) != null }) return null
            "FF$value"
        }
        8 -> {
            if (!value.all { it.digitToIntOrNull(16) != null }) return null
            value
        }
        3 -> {
            if (!value.all { it.digitToIntOrNull(16) != null }) return null
            val expanded = value.map { "$it$it" }.joinToString("")
            "FF$expanded"
        }
        else -> return null
    }
    return runCatching {
        val alpha = argb.substring(0, 2).toInt(16)
        val red = argb.substring(2, 4).toInt(16)
        val green = argb.substring(4, 6).toInt(16)
        val blue = argb.substring(6, 8).toInt(16)
        Color(red, green, blue, alpha)
    }.getOrNull()
}
```

### 背景明暗判断

```kotlin
internal fun Color.isVisuallyDarkBackground(): Boolean {
    fun linearize(c: Float): Float {
        val x = c.coerceIn(0f, 1f)
        return if (x <= 0.03928f) x / 12.92f else ((x + 0.055f) / 1.055f).pow(2.4).toFloat()
    }
    val r = linearize(red)
    val g = linearize(green)
    val b = linearize(blue)
    val luminance = 0.2126f * r + 0.7152f * g + 0.0722f * b
    return luminance < 0.45f
}
```

> 项目中的明暗判断 (`src/main/kotlin/app/UiColorHex.kt:39-49`):
```kotlin
internal fun Color.isVisuallyDarkBackground(): Boolean {
    fun linearize(c: Float): Float {
        val x = c.coerceIn(0f, 1f)
        return if (x <= 0.03928f) x / 12.92f else ((x + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
    }
    val r = linearize(red)
    val g = linearize(green)
    val b = linearize(blue)
    val luminance = 0.2126f * r + 0.7152f * g + 0.0722f * b
    return luminance < 0.45f
}
```

### 动态主题颜色

```kotlin
fun appMaterialColors(isDark: Boolean, backgroundHex: String?): Colors {
    val background = backgroundHex?.let { parseHexColorOrNull(it) }
    return if (background?.isVisuallyDarkBackground() == true) {
        darkColors().copy(background = background)
    } else if (background != null) {
        lightColors().copy(background = background)
    } else {
        if (isDark) darkColors() else lightColors()
    }
}
```

## 11.4 方法颜色高亮

### HTTP 方法颜色

```kotlin
fun methodColor(method: String): Color = when (method.uppercase()) {
    "GET" -> Color(0xFF4CAF50)    // 绿色
    "POST" -> Color(0xFFFFC107)   // 黄色
    "PUT" -> Color(0xFF2196F3)    // 蓝色
    "PATCH" -> Color(0xFF9C27B0)  // 紫色
    "DELETE" -> Color(0xFFF44336) // 红色
    "OPTIONS" -> Color(0xFF795548) // 棕色
    "HEAD" -> Color(0xFF607D8B)   // 灰蓝色
    else -> Color.Gray
}
```

> 项目中的实现 (`src/main/kotlin/http/RequestPanel.kt`):
```kotlin
fun methodColor(method: String): Color = when (method.uppercase()) {
    "GET" -> Color(0xFF4CAF50)
    "POST" -> Color(0xFFFFC107)
    "PUT" -> Color(0xFF2196F3)
    "PATCH" -> Color(0xFF9C27B0)
    "DELETE" -> Color(0xFFF44336)
    "OPTIONS", "HEAD" -> Color(0xFF607D8B)
    else -> Color.Gray
}
```

## 11.5 状态码颜色

```kotlin
fun statusCodeColor(code: Int): Color = when {
    code in 200..299 -> Color(0xFF4CAF50)  // 2xx 绿色
    code in 300..399 -> Color(0xFF03A9F4)  // 3xx 蓝色
    code in 400..499 -> Color(0xFFFF9800)  // 4xx 橙色
    code in 500..599 -> Color(0xFFF44336) // 5xx 红色
    else -> Color.Gray
}
```

## 11.6 Typography 字体

```kotlin
val Typography = Typography(
    body1 = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    h1 = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
    ),
    // ...
)

Text(
    text = "标题",
    style = MaterialTheme.typography.h1
)
```

> 项目中的排版 (`src/main/kotlin/app/AppTypography.kt`):
```kotlin
val Typography = Typography(
    body1 = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    h4 = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
    ),
    // ...
)
```

## 11.7 总结

| 功能 | 实现 |
|------|------|
| 深/浅主题 | `darkColors()` / `lightColors()` |
| 主题切换 | `isDarkTheme` 状态 |
| Hex 解析 | `parseHexColorOrNull` |
| 明暗判断 | 亮度计算 `luminance < 0.45` |
| 方法颜色 | `methodColor` 函数 |
| 状态码颜色 | 区间判断 |

**下篇**：桌面应用快捷键绑定