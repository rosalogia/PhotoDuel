package dev.rosalogia.photoduel.core.ui

import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Thin line icons on a 24-unit grid, to match the type's light weights. [Icon] tints them like
 * any other vector.
 */
object BrandIcons {
    val Back = line("Back", "M15 5L8 12L15 19")
    val Close = line("Close", "M6 6L18 18M18 6L6 18")
    val Undo = line("Undo", "M9 6L5 10L9 14M5 10H15A4.5 4.5 0 0 1 15 19H11")
    val Library = line("Library", "M4 4H10V10H4ZM14 4H20V10H14ZM4 14H10V20H4ZM14 14H20V20H14Z")
    val Settings = line(
        "Settings",
        "M4 8H7M11 8H20M4 16H13M17 16H20M9 8m-2 0a2 2 0 1 0 4 0a2 2 0 1 0 -4 0M15 16m-2 0a2 2 0 1 0 4 0a2 2 0 1 0 -4 0",
    )
    val Trash = line("Trash", "M5 7H19M10 7V4.5H14V7M7 7L8 19.5H16L17 7")
    val Share = line("Share", "M12 4V14.5M8 8L12 4L16 8M6 11.5V19.5H18V11.5")
    val Heart = line("Heart", "M12 19C6 15 4 12 4 9.2A4 4 0 0 1 12 7.5A4 4 0 0 1 20 9.2C20 12 18 15 12 19Z")
    val Edit = line("Edit", "M5 19L6 15L15 6L18 9L9 18ZM13 8L16 11")
    val Calendar = line("Calendar", "M5 6H19V19H5ZM5 10H19M9 4V8M15 4V8")
    val Pair = line("Pair", "M4 7.5H13V19H4ZM11 5H20V16.5H13")
    val Keyboard = line("Keyboard", "M3 7H21V17H3ZM8 14H16M7 10.5H7.01M10.3 10.5H10.31M13.6 10.5H13.61M17 10.5H17.01")
    val Folder = line("Folder", "M3.5 6.5V18.5H20.5V9H11.5L9.5 6.5Z")
    val Check = line("Check", "M5 12.5L10 17L19 7")
    val Plus = line("Plus", "M12 5V19M5 12H19")
    val Open = line("Open", "M14 5H19V10M19 5L11 13M17 14V19H5V7H10")

    private fun line(name: String, path: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(
            addPathNodes(path),
            stroke = SolidColor(androidx.compose.ui.graphics.Color.Black),
            strokeLineWidth = 1.6f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
        .build()
}
