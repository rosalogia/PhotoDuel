package dev.rosalogia.photoduel.debug

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import dev.rosalogia.photoduel.BuildConfig
import dev.rosalogia.photoduel.core.common.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MAX_SHOWN_CHARS = 200_000

/** Writes device info, the last crash and the full log to one file and opens the share sheet. */
fun shareLogs(context: Context) {
    val dir = File(context.cacheDir, "shared").apply { mkdirs() }
    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
    val file = File(dir, "photoduel-logs-$stamp.txt")
    dir.listFiles()?.filter { it.name.startsWith("photoduel-logs-") }?.forEach { it.delete() }
    file.writeText(
        buildString {
            appendLine("Photo Duel ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${BuildConfig.BUILD_TYPE}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine()
            AppLog.crashFile?.takeIf { it.exists() }?.let {
                appendLine("=== Last crash ===")
                appendLine(it.readText())
                appendLine()
            }
            appendLine("=== Log ===")
            append(AppLog.readAll())
        },
    )
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "Photo Duel logs $stamp")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Share logs"))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugLogScreen() {
    val context = LocalContext.current
    var text by remember { mutableStateOf("Loading…") }
    LaunchedEffect(Unit) {
        text = withContext(Dispatchers.IO) { AppLog.readAll().takeLast(MAX_SHOWN_CHARS).ifEmpty { "(empty)" } }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Logs") },
                actions = { TextButton(onClick = { shareLogs(context) }) { Text("Share") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())) {
            SelectionContainer {
                Text(
                    text,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
    }
}

/** If the app crashed last time, offers to share the report once, then forgets it. */
@Composable
fun CrashReportPrompt() {
    val context = LocalContext.current
    var crash by remember { mutableStateOf(AppLog.crashFile?.takeIf { it.exists() }?.readText()) }
    val report = crash ?: return
    fun dismiss() {
        AppLog.crashFile?.delete()
        crash = null
    }
    AlertDialog(
        onDismissRequest = ::dismiss,
        title = { Text("Photo Duel crashed last time") },
        text = { Text(report.lineSequence().take(6).joinToString("\n"), fontFamily = FontFamily.Monospace, fontSize = 11.sp) },
        confirmButton = {
            TextButton(onClick = {
                shareLogs(context)
                dismiss()
            }) { Text("Share report") }
        },
        dismissButton = { TextButton(onClick = ::dismiss) { Text("Dismiss") } },
    )
}
