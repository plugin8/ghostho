package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.viewmodel.PyHostViewModel
import com.example.viewmodel.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeEditorScreen(
    viewModel: PyHostViewModel
) {
    val file by viewModel.editingFile.collectAsState()
    val content by viewModel.editorContent.collectAsState()
    val isDirty by viewModel.isEditorDirty.collectAsState()
    val isHosting by viewModel.isHosting.collectAsState()
    val hostingFileName by viewModel.hostingFileName.collectAsState()

    var showPipDialog by remember { mutableStateOf(false) }

    BackHandler {
        if (isDirty) {
            viewModel.saveEditorContent()
        }
        viewModel.navigateTo(Screen.FILE_LIST)
    }

    if (showPipDialog) {
        PipManagerDialog(viewModel = viewModel, onDismiss = { showPipDialog = false })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = (file?.fileName ?: "editor.py") + if (isDirty) " *" else "",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isDirty) PyYellow else TerminalText
                            )
                            if (isHosting && hostingFileName == file?.fileName) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = PyGreen.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "HOSTING",
                                        color = PyGreen,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = "${content.lines().size} lines • ${content.toByteArray().size} bytes",
                            style = MaterialTheme.typography.bodySmall,
                            color = TerminalMuted
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isDirty) viewModel.saveEditorContent()
                            viewModel.navigateTo(Screen.FILE_LIST)
                        },
                        modifier = Modifier.testTag("editor_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TerminalText
                        )
                    }
                },
                actions = {
                    // Pip Dependencies quick button
                    IconButton(
                        onClick = { showPipDialog = true },
                        modifier = Modifier.testTag("editor_pip_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Extension,
                            contentDescription = "Dependencies",
                            tint = PyCyan
                        )
                    }

                    // Save Button
                    IconButton(
                        onClick = { viewModel.saveEditorContent() },
                        enabled = isDirty,
                        modifier = Modifier.testTag("editor_save_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "Save",
                            tint = if (isDirty) PyYellow else TerminalMuted
                        )
                    }

                    // Host / Run Button
                    FilledTonalButton(
                        onClick = { viewModel.hostCurrentEditorFile() },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = PyGreen,
                            contentColor = PyDarkBackground
                        ),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("editor_host_button"),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Host",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PyDarkSurface
                )
            )
        },
        bottomBar = {
            // Quick Code Shortcuts Bar
            Surface(
                color = PyDarkSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val shortcuts = listOf(
                        "    " to "Tab",
                        ":" to ":",
                        "()" to "()",
                        "\"\"" to "\"\"",
                        "''" to "''",
                        " = " to "=",
                        "_" to "_",
                        "#" to "#",
                        "def " to "def",
                        "import " to "import",
                        "return " to "return",
                        "print(" to "print",
                        "@bot." to "@bot"
                    )

                    shortcuts.forEach { (insertText, label) ->
                        OutlinedButton(
                            onClick = {
                                viewModel.updateEditorContent(content + insertText)
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = TerminalText
                            )
                        ) {
                            Text(
                                text = label,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        },
        containerColor = TerminalBackground
    ) { innerPadding ->
        val lines = content.lines()
        val lineCount = lines.size
        val verticalScrollState = rememberScrollState()
        val horizontalScrollState = rememberScrollState()

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(TerminalBackground)
        ) {
            // Line numbers gutter
            Column(
                modifier = Modifier
                    .width(42.dp)
                    .fillMaxHeight()
                    .background(TerminalGutter)
                    .verticalScroll(verticalScrollState)
                    .padding(vertical = 12.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.End
            ) {
                for (i in 1..maxOf(1, lineCount)) {
                    Text(
                        text = "$i",
                        color = TerminalMuted.copy(alpha = 0.6f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                }
            }

            // Code Text Field
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(verticalScrollState)
                    .horizontalScroll(horizontalScrollState)
                    .padding(12.dp)
            ) {
                BasicTextField(
                    value = content,
                    onValueChange = { viewModel.updateEditorContent(it) },
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = TerminalText
                    ),
                    cursorBrush = SolidColor(PyCyan),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("code_editor_text_field")
                )
            }
        }
    }
}
