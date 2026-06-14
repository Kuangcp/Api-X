package app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

private fun ImageVector.Builder.addPathSegments(
    pathData: String,
    fill: Color = Color.Black,
    fillAlpha: Float = 1f,
    stroke: Color = Color.Black,
    strokeAlpha: Float = 1f,
    strokeLineWidth: Float = 0f,
    strokeLineCap: StrokeCap = StrokeCap.Butt,
    strokeLineJoin: StrokeJoin = StrokeJoin.Miter,
    strokeLineMiter: Float = 4f,
    pathFillType: PathFillType = PathFillType.NonZero,
): ImageVector.Builder {
    val parser = androidx.compose.ui.graphics.vector.PathParser()
    parser.parsePathString(pathData)
    val nodes = parser.toNodes()
    path(
        fill = SolidColor(fill),
        fillAlpha = fillAlpha,
        stroke = SolidColor(stroke),
        strokeAlpha = strokeAlpha,
        strokeLineWidth = strokeLineWidth,
        strokeLineCap = strokeLineCap,
        strokeLineJoin = strokeLineJoin,
        strokeLineMiter = strokeLineMiter,
        pathFillType = pathFillType,
    ) {
        for (node in nodes) {
            when (node) {
                is androidx.compose.ui.graphics.vector.PathNode.MoveTo ->
                    moveTo(node.x, node.y)
                is androidx.compose.ui.graphics.vector.PathNode.RelativeMoveTo ->
                    moveToRelative(node.dx, node.dy)
                is androidx.compose.ui.graphics.vector.PathNode.LineTo ->
                    lineTo(node.x, node.y)
                is androidx.compose.ui.graphics.vector.PathNode.RelativeLineTo ->
                    lineToRelative(node.dx, node.dy)
                is androidx.compose.ui.graphics.vector.PathNode.HorizontalTo ->
                    horizontalLineTo(node.x)
                is androidx.compose.ui.graphics.vector.PathNode.RelativeHorizontalTo ->
                    horizontalLineToRelative(node.dx)
                is androidx.compose.ui.graphics.vector.PathNode.VerticalTo ->
                    verticalLineTo(node.y)
                is androidx.compose.ui.graphics.vector.PathNode.RelativeVerticalTo ->
                    verticalLineToRelative(node.dy)
                is androidx.compose.ui.graphics.vector.PathNode.CurveTo ->
                    curveTo(node.x1, node.y1, node.x2, node.y2, node.x3, node.y3)
                is androidx.compose.ui.graphics.vector.PathNode.RelativeCurveTo ->
                    curveToRelative(node.dx1, node.dy1, node.dx2, node.dy2, node.dx3, node.dy3)
                is androidx.compose.ui.graphics.vector.PathNode.ReflectiveCurveTo ->
                    reflectiveCurveTo(node.x1, node.y1, node.x2, node.y2)
                is androidx.compose.ui.graphics.vector.PathNode.RelativeReflectiveCurveTo ->
                    reflectiveCurveToRelative(node.dx1, node.dy1, node.dx2, node.dy2)
                is androidx.compose.ui.graphics.vector.PathNode.QuadTo ->
                    quadTo(node.x1, node.y1, node.x2, node.y2)
                is androidx.compose.ui.graphics.vector.PathNode.RelativeQuadTo ->
                    quadToRelative(node.dx1, node.dy1, node.dx2, node.dy2)
                is androidx.compose.ui.graphics.vector.PathNode.ReflectiveQuadTo ->
                    reflectiveQuadTo(node.x, node.y)
                is androidx.compose.ui.graphics.vector.PathNode.RelativeReflectiveQuadTo ->
                    reflectiveQuadToRelative(node.dx, node.dy)
                is androidx.compose.ui.graphics.vector.PathNode.ArcTo ->
                    arcTo(node.horizontalEllipseRadius, node.verticalEllipseRadius, node.theta, node.isMoreThanHalf, node.isPositiveArc, node.arcStartX, node.arcStartY)
                is androidx.compose.ui.graphics.vector.PathNode.RelativeArcTo ->
                    arcToRelative(node.horizontalEllipseRadius, node.verticalEllipseRadius, node.theta, node.isMoreThanHalf, node.isPositiveArc, node.arcStartDx, node.arcStartDy)
                is androidx.compose.ui.graphics.vector.PathNode.Close ->
                    close()
            }
        }
    }
    return this
}

object CustomIcons {

    /** 替代 Icons.Filled.AccessTime — 时钟图标 */
    val AccessTime: ImageVector by lazy {
        ImageVector.Builder(
            name = "AccessTime",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            addPathSegments(
                pathData = "M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zM12 20c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8zm.5-13H11v6l5.25 3.15.75-1.23-4.5-2.67z",
                fill = Color.Black,
            )
        }.build()
    }

    /** 替代 Icons.Filled.AutoAwesome — 星形闪光图标 */
    val AutoAwesome: ImageVector by lazy {
        ImageVector.Builder(
            name = "AutoAwesome",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            addPathSegments(
                pathData = "M19 9l1.25-2.75L23 5l-2.75-1.25L19 1l-1.25 2.75L15 5l2.75 1.25zm-7.5.5L9 4 6.5 9.5 1 12l5.5 2.5L9 20l2.5-5.5L17 12l-5.5-2.5zM19 15l-1.25 2.75L15 19l2.75 1.25L19 23l1.25-2.75L23 19l-2.75-1.25z",
                fill = Color.Black,
            )
        }.build()
    }

    /** 替代 Icons.Filled.CloudDownload — 云下载图标 */
    val CloudDownload: ImageVector by lazy {
        ImageVector.Builder(
            name = "CloudDownload",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            addPathSegments(
                pathData = "M19.35 10.04C18.67 6.59 15.64 4 12 4 9.11 4 6.6 5.64 5.35 8.04 2.34 8.36 0 10.91 0 14c0 3.31 2.69 6 6 6h13c2.76 0 5-2.24 5-5 0-2.64-2.05-4.78-4.65-4.96zM17 13l-5 5-5-5h3V9h4v4h3z",
                fill = Color.Black,
            )
        }.build()
    }

    /** 替代 Icons.Filled.CloudUpload — 云上传图标 */
    val CloudUpload: ImageVector by lazy {
        ImageVector.Builder(
            name = "CloudUpload",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            addPathSegments(
                pathData = "M19.35 10.04C18.67 6.59 15.64 4 12 4 9.11 4 6.6 5.64 5.35 8.04 2.34 8.36 0 10.91 0 14c0 3.31 2.69 6 6 6h13c2.76 0 5-2.24 5-5 0-2.64-2.05-4.78-4.65-4.96zM14 13v4h-4v-4H7l5-5 5 5h-3z",
                fill = Color.Black,
            )
        }.build()
    }

    /** 替代 Icons.Filled.CreateNewFolder — 新建文件夹图标 */
    val CreateNewFolder: ImageVector by lazy {
        ImageVector.Builder(
            name = "CreateNewFolder",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            addPathSegments(
                pathData = "M20 6h-8l-2-2H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2zm-1 8h-3v3h-2v-3h-3v-2h3V9h2v3h3v2z",
                fill = Color.Black,
            )
        }.build()
    }

    /** 替代 Icons.Filled.DarkMode — 月亮图标 */
    val DarkMode: ImageVector by lazy {
        ImageVector.Builder(
            name = "DarkMode",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            addPathSegments(
                pathData = "M12 3c-4.97 0-9 4.03-9 9s4.03 9 9 9 9-4.03 9-9c0-.46-.04-.92-.1-1.36-.98 1.37-2.58 2.26-4.4 2.26-2.98 0-5.4-2.42-5.4-5.4 0-1.81.89-3.42 2.26-4.4-.44-.06-.9-.1-1.36-.1z",
                fill = Color.Black,
            )
        }.build()
    }

    /** 替代 Icons.Filled.LibraryAdd — 图书馆加号图标 */
    val LibraryAdd: ImageVector by lazy {
        ImageVector.Builder(
            name = "LibraryAdd",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            addPathSegments(
                pathData = "M4 6H2v14c0 1.1.9 2 2 2h14v-2H4V6zm16-8H8c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm-1 9h-4v4h-2v-4H9V9h4V5h2v4h4v2z",
                fill = Color.Black,
            )
        }.build()
    }

    /** 替代 Icons.AutoMirrored.Filled.LibraryBooks — 图书馆图标 */
    val LibraryBooks: ImageVector by lazy {
        ImageVector.Builder(
            name = "LibraryBooks",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            addPathSegments(
                pathData = "M18 2H6c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zM6 4h5v8l-2.5-1.5L6 12V4z",
                fill = Color.Black,
            )
        }.build()
    }

    /** 替代 Icons.Filled.LightMode — 太阳图标 */
    val LightMode: ImageVector by lazy {
        ImageVector.Builder(
            name = "LightMode",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            addPathSegments(
                pathData = "M12 7c-2.76 0-5 2.24-5 5s2.24 5 5 5 5-2.24 5-5-2.24-5-5-5zM2 13h2c.55 0 1-.45 1-1s-.45-1-1-1H2c-.55 0-1 .45-1 1s.45 1 1 1zm18 0h2c.55 0 1-.45 1-1s-.45-1-1-1h-2c-.55 0-1 .45-1 1s.45 1 1 1zM11 2v2c0 .55.45 1 1 1s1-.45 1-1V2c0-.55-.45-1-1-1s-1 .45-1 1zm0 18v2c0 .55.45 1 1 1s1-.45 1-1v-2c0-.55-.45-1-1-1s-1 .45-1 1zM5.99 4.58c-.39-.39-1.03-.39-1.42 0-.39.39-.39 1.03 0 1.42l1.06 1.06c.39.39 1.03.39 1.42 0 .38-.39.39-1.03 0-1.42L5.99 4.58zm12.03 12.02c-.39-.39-1.03-.39-1.42 0-.39.39-.39 1.03 0 1.42l1.06 1.06c.39.39 1.03.39 1.42 0 .39-.39.39-1.03 0-1.42l-1.06-1.06zm1.06-10.96c.39-.39.39-1.03 0-1.42-.39-.39-1.03-.39-1.42 0l-1.06 1.06c-.39.39-.39 1.03 0 1.42.39.38 1.03.39 1.42 0l1.06-1.06zM7.05 18.36c.39-.39.39-1.03 0-1.42-.39-.39-1.03-.39-1.42 0l-1.06 1.06c-.39.39-.39 1.03 0 1.42.39.39 1.03.39 1.42 0l1.06-1.06z",
                fill = Color.Black,
            )
        }.build()
    }

    /** 替代 Icons.Filled.PostAdd — 文章加号图标 */
    val PostAdd: ImageVector by lazy {
        ImageVector.Builder(
            name = "PostAdd",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            addPathSegments(
                pathData = "M20 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 14H4V8h16v10zm-2-1h-2v-2h2v2zm-4 0h-2v-2h2v2zm-4 0H8v-2h2v2zm8-4h-2v-2h2v2zm-4 0h-2v-2h2v2zm-4 0H8v-2h2v2zm8-4h-2V7h2v2zm-4 0h-2V7h2v2zm-4 0H8V7h2v2z",
                fill = Color.Black,
            )
        }.build()
    }

    /** 替代 Icons.Filled.ContentCopy — 复制图标 */
    val ContentCopy: ImageVector by lazy {
        ImageVector.Builder(
            name = "ContentCopy",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            addPathSegments(
                pathData = "M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z",
                fill = Color.Black,
            )
        }.build()
    }

    /** 替代 Icons.Filled.ContentPaste — 粘贴图标 */
    val ContentPaste: ImageVector by lazy {
        ImageVector.Builder(
            name = "ContentPaste",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            addPathSegments(
                pathData = "M19 2h-4.18C14.4.84 13.3 0 12 0c-1.3 0-2.4.84-2.82 2H5c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm-7 0c.55 0 1 .45 1 1s-.45 1-1 1-1-.45-1-1 .45-1 1-1zm7 18H5V4h2v3h10V4h2v16z",
                fill = Color.Black,
            )
        }.build()
    }

    /** 替代 Icons.Filled.CropSquare — 方框图标 */
    val CropSquare: ImageVector by lazy {
        ImageVector.Builder(
            name = "CropSquare",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            addPathSegments(
                pathData = "M3 3v18h18V3H3zm8 16H5v-6h6v6zm0-8H5V5h6v6zm8 8h-6v-6h6v6zm0-8h-6V5h6v6z",
                fill = Color.Black,
            )
        }.build()
    }

    /** 替代 Icons.Filled.FilterNone — 重叠方框图标 */
    val FilterNone: ImageVector by lazy {
        ImageVector.Builder(
            name = "FilterNone",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            addPathSegments(
                pathData = "M3 5v14h18V5H3zm4 2v10H5V7h2zm12 10h-2V7h2v10z",
                fill = Color.Black,
            )
        }.build()
    }

    /** 替代 Icons.Filled.Remove — 减号图标 */
    val Remove: ImageVector by lazy {
        ImageVector.Builder(
            name = "Remove",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            addPathSegments(
                pathData = "M19 13H5v-2h14v2z",
                fill = Color.Black,
            )
        }.build()
    }

    /** 替代 Icons.Filled.Folder — 文件夹图标 */
    val Folder: ImageVector by lazy {
        ImageVector.Builder(
            name = "Folder",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            addPathSegments(
                pathData = "M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z",
                fill = Color.Black,
            )
        }.build()
    }

    /** 替代 Icons.Filled.FolderOpen — 打开文件夹图标 */
    val FolderOpen: ImageVector by lazy {
        ImageVector.Builder(
            name = "FolderOpen",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            addPathSegments(
                pathData = "M20 6h-8l-2-2H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2zm0 12H4V8h16v10z",
                fill = Color.Black,
            )
        }.build()
    }
}
