package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.LogEntry
import com.example.engine.LogType
import com.example.ui.theme.*
import com.example.viewmodel.PyHostViewModel
import com.example.viewmodel.Screen
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    viewModel: PyHostViewModel
) {
    val context = LocalContext.current
    val isHosting by viewModel.isHosting.collectAsState()
    val hostingFileName by viewModel.hostingFileName.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val startTime by viewModel.startTime.collectAsState()
    val updatesProcessed by viewModel.updatesProcessed.collectAsState()
    val socketActive by viewModel.socketActive.collectAsState()
    val botUsername by viewModel.botUsername.collectAsState()

    var stdinText by remember { mutableStateOf("") }
    var autoScroll by remember { mutableStateOf(true) }

    // Elapsed timer calculation
    var elapsedSeconds by remember { mutableLongStateOf(0L) }
    LaunchedEffect(isHosting, startTime) {
        if (isHosting && startTime > 0) {
            while (true) {
                elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000
                delay(1000)
            }
        } else {
            elapsedSeconds = 0L
        }
    }

    val formattedUptime = remember(elapsedSeconds) {
        val hrs = elapsedSeconds / 3600
        val mins = (elapsedSeconds % 3600) / 60
        val secs = elapsedSeconds % 60
        if (hrs > 0) {
            String.format("%02d:%02d:%02d", hrs, mins, secs)
        } else {
            String.format("%02d:%02d", mins, secs)
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(logs.size, autoScroll) {
        if (autoScroll && logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    BackHandler {
        viewModel.navigateTo(Screen.FILE_LIST)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = hostingFileName ?: "Python Terminal",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TerminalText
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            // Status Dot & Badge
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(if (isHosting) PyGreen else PyRed, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isHosting) "ONLINE" else "OFFLINE",
                                color = if (isHosting) PyGreen else PyRed,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Uptime: $formattedUptime",
                                fontSize = 12.sp,
                                color = TerminalMuted,
                                fontFamily = FontFamily.Monospace
                            )
                            if (botUsername != null) {
                                Text(
                                    text = " • Bot: $botUsername",
                                    fontSize = 12.sp,
                                    color = PyPurple,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            if (updatesProcessed > 0) {
                                Text(
                                    text = " • Updates: $updatesProcessed",
                                    fontSize = 12.sp,
                                    color = PyCyan
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.navigateTo(Screen.FILE_LIST) },
                        modifier = Modifier.testTag("terminal_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TerminalText
                        )
                    }
                },
                actions = {
                    // Copy logs
                    IconButton(
                        onClick = {
                            val text = logs.joinToString("\n") { "[${it.formattedTime}] ${it.message}" }
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Terminal Logs", text))
                            Toast.makeText(context, "Logs copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.testTag("copy_logs_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Logs",
                            tint = TerminalMuted
                        )
                    }

                    // Clear logs
                    IconButton(
                        onClick = { viewModel.clearLogs() },
                        modifier = Modifier.testTag("clear_logs_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear Logs",
                            tint = TerminalMuted
                        )
                    }

                    // Restart / Stop Button
                    if (isHosting) {
                        FilledTonalButton(
                            onClick = { viewModel.stopHosting() },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = PyRed.copy(alpha = 0.2f),
                                contentColor = PyRed
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .testTag("stop_host_button")
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Stop", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        FilledTonalButton(
                            onClick = { viewModel.restartHosting() },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = PyGreen,
                                contentColor = PyDarkBackground
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .testTag("restart_host_button")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Start", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PyDarkSurface
                )
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PyDarkSurface)
                    .imePadding()
            ) {
                // Secondary bar with Auto-scroll toggle and socket status
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = if (socketActive) PyGreen else PyYellow,
                            modifier = Modifier.size(6.dp)
                        ) {}
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (socketActive) "SSL Socket: Persistent Keep-Alive" else "Socket Standby",
                            fontSize = 11.sp,
                            color = TerminalMuted
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.testTag("auto_scroll_toggle")
                    ) {
                        Text(
                            text = if (autoScroll) "Auto-scroll ON" else "Auto-scroll OFF",
                            fontSize = 11.sp,
                            color = if (autoScroll) PyCyan else TerminalMuted
                        )
                        IconButton(
                            onClick = { autoScroll = !autoScroll },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = if (autoScroll) Icons.Default.VerticalAlignBottom else Icons.Default.Pause,
                                contentDescription = "Auto Scroll",
                                tint = if (autoScroll) PyCyan else TerminalMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Stdin Input row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = stdinText,
                        onValueChange = { stdinText = it },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("stdin_input_field"),
                        placeholder = { Text("Send stdin input...", color = TerminalMuted, fontSize = 13.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PyCyan,
                            unfocusedBorderColor = PyCardBorder,
                            focusedTextColor = TerminalText,
                            unfocusedTextColor = TerminalText,
                            focusedContainerColor = TerminalBackground,
                            unfocusedContainerColor = TerminalBackground
                        ),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp
                        )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            if (stdinText.isNotBlank()) {
                                viewModel.sendStdin(stdinText)
                                stdinText = ""
                            }
                        },
                        enabled = stdinText.isNotBlank(),
                        modifier = Modifier
                            .background(if (stdinText.isNotBlank()) PyCyan else PyDarkSurfaceVariant, CircleShape)
                            .testTag("stdin_send_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (stdinText.isNotBlank()) PyDarkBackground else TerminalMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        },
        containerColor = TerminalBackground
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(TerminalBackground)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            tint = TerminalMuted.copy(alpha = 0.4f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Terminal Ready",
                            color = TerminalMuted,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("terminal_logs_list")
                ) {
                    items(logs, key = { it.id }) { log ->
                        TerminalLogLine(log = log)
                    }
                }
            }
        }
    }
}

@Composable
fun TerminalLogLine(log: LogEntry) {
    val textColor = when (log.type) {
        LogType.STDOUT -> TerminalText
        LogType.STDERR -> PyRed
        LogType.INFO -> PyCyan
        LogType.WARN -> PyYellow
        LogType.TELEGRAM -> PyPurple
        LogType.SUCCESS -> PyGreen
        LogType.NETWORK -> PyOrange
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
    ) {
        Text(
            text = log.formattedTime,
            color = TerminalMuted.copy(alpha = 0.5f),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text = log.message,
            color = textColor,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )
    }
}
