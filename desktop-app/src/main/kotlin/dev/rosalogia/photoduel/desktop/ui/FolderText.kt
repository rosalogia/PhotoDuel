package dev.rosalogia.photoduel.desktop.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import dev.rosalogia.photoduel.desktop.AppDirs
import java.io.File

/** A folder's path on one line: home shortened to ~ (except on Windows), the middle elided if long. */
@Composable
fun FolderText(folder: File, style: TextStyle, modifier: Modifier = Modifier) {
    Text(displayPath(folder), modifier, style = style, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
}

fun displayPath(folder: File): String {
    val path = folder.path
    val home = System.getProperty("user.home").trimEnd(File.separatorChar)
    if (AppDirs.isWindows || home.isEmpty()) return path
    return when {
        path == home -> "~"
        path.startsWith(home + File.separator) -> "~" + path.substring(home.length)
        else -> path
    }
}
