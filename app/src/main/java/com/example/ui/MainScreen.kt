package com.example.ui

import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PyFileEntity
import com.example.ui.theme.*
import com.example.viewmodel.PyHostViewModel
import com.example.viewmodel.Screen
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: PyHostViewModel
) {
    val context = LocalContext.current
    val files by viewModel.files.collectAsState()
    val isHosting by viewModel.isHosting.collectAsState()
    val hostingFileName by viewModel.hostingFileName.collectAsState()
    val startTime by viewModel.startTime.collectAsState()
    val updatesProcessed by viewModel.updatesProcessed.collectAsState()
    val botUsername by viewModel.botUsername.collectAsState()
    val selectedIds by viewModel.selectedFileIds.collectAsState()
    val isMultiSelect by viewModel.isMultiSelectMode.collectAsState()
    val isPipDialogVisible by viewModel.isPipDialogVisible.collectAsState()

    var showCreateFileDialog by remember { mutableStateOf(false) }
    var newFileNameInput by remember { mutableStateOf("") }
    var fileToDelete by remember { mutableStateOf<PyFileEntity?>(null) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    var showBatteryDialog by remember { mutableStateOf(false) }

    val isBatteryOptimized = remember {
        !BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
    }

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
        if (hrs > 0) String.format("%02d:%02d:%02d", hrs, mins, secs)
        else String.format("%02d:%02d", mins, secs)
    }

    // System File Picker for Python files (.py)
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                var displayName = "script.py"
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIndex >= 0) {
                        displayName = cursor.getString(nameIndex)
                    }
                }
                val content = context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader().use { it.readText() }
                } ?: ""

                viewModel.importPythonFile(displayName, content)
                Toast.makeText(context, "Loaded $displayName", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Error importing file: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    if (isPipDialogVisible) {
        PipManagerDialog(viewModel = viewModel, onDismiss = { viewModel.closePipDialog() })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            tint = PyCyan,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "PyHost",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = TerminalText
                            )
                            Text(
                                text = "Python 24/7 Engine Daemon",
                                style = MaterialTheme.typography.labelSmall,
                                color = TerminalMuted
                            )
                        }
                    }
                },
                actions = {
                    // Battery exemption alert icon
                    if (isBatteryOptimized) {
                        IconButton(
                            onClick = { showBatteryDialog = true },
                            modifier = Modifier.testTag("battery_optimization_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.BatteryAlert,
                                contentDescription = "Battery Optimization",
                                tint = PyYellow
                            )
                        }
                    }

                    // PIP Package Manager
                    IconButton(
                        onClick = { viewModel.openPipDialog() },
                        modifier = Modifier.testTag("main_pip_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Extension,
                            contentDescription = "PIP Packages",
                            tint = PyCyan
                        )
                    }

                    // Batch delete actions
                    if (isMultiSelect) {
                        IconButton(
                            onClick = { showBatchDeleteDialog = true },
                            modifier = Modifier.testTag("batch_delete_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Selected",
                                tint = PyRed
                            )
                        }
                        IconButton(
                            onClick = { viewModel.clearSelection() },
                            modifier = Modifier.testTag("cancel_selection_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel Selection",
                                tint = TerminalMuted
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PyDarkSurface)
            )
        },
        containerColor = PyDarkBackground
    ) { innerPadding ->
        if (files.isEmpty()) {
            // First-Time / Empty State: Full Page Prominent Python File Selector
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, PyCyan, RoundedCornerShape(20.dp))
                        .testTag("select_python_file_card"),
                    colors = CardDefaults.cardColors(containerColor = PyDarkSurface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = PyCyan.copy(alpha = 0.15f),
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.UploadFile,
                                    contentDescription = null,
                                    tint = PyCyan,
                                    modifier = Modifier.size(38.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Select Python (.py) File",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = TerminalText
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "ဖုန်းထဲရှိ Python script သို့မဟုတ် Telegram Bot (.py) ဖိုင်ကို ရွေးချယ်ပြီး နောက်ကွယ်တွင် 24/7 Host ပြုလုပ်နိုင်ပါသည်။",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TerminalMuted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = { filePickerLauncher.launch("*/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = PyCyan),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("choose_file_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = PyDarkBackground,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Choose .py File from Phone",
                                color = PyDarkBackground,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedButton(
                            onClick = {
                                newFileNameInput = "bot.py"
                                showCreateFileDialog = true
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(PyCardBorder)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("create_new_file_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = PyCyan,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Create New Script",
                                color = TerminalText,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        } else {
            // Files List Dashboard
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(2.dp))
                }

                // 1. ACTIVE HOSTING STATUS BANNER
                if (isHosting) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, PyGreen, RoundedCornerShape(14.dp))
                                .clickable { viewModel.navigateTo(Screen.TERMINAL) }
                                .testTag("active_host_banner"),
                            colors = CardDefaults.cardColors(containerColor = PyDarkSurfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .background(PyGreen, CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "RUNNING IN FOREGROUND",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = PyGreen,
                                            letterSpacing = 1.sp
                                        )
                                    }

                                    Surface(
                                        color = TerminalBackground,
                                        shape = RoundedCornerShape(6.dp),
                                        border = CardDefaults.outlinedCardBorder()
                                    ) {
                                        Text(
                                            text = formattedUptime,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 12.sp,
                                            color = TerminalText,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = hostingFileName ?: "script.py",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TerminalText,
                                    fontFamily = FontFamily.Monospace
                                )

                                if (botUsername != null || updatesProcessed > 0) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Bot: ${botUsername ?: "Active"} • Handled: $updatesProcessed updates",
                                        fontSize = 12.sp,
                                        color = PyPurple
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { viewModel.navigateTo(Screen.TERMINAL) },
                                        colors = ButtonDefaults.buttonColors(containerColor = PyCyan),
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(vertical = 8.dp)
                                    ) {
                                        Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Open Terminal", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PyDarkBackground)
                                    }

                                    OutlinedButton(
                                        onClick = { viewModel.stopHosting() },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PyRed),
                                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(PyRed)),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                    ) {
                                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Stop", fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. QUICK UPLOAD / NEW FILE BAR
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { filePickerLauncher.launch("*/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = PyCyan),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("choose_file_button"),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null, tint = PyDarkBackground, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Select .py File", color = PyDarkBackground, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                newFileNameInput = "bot.py"
                                showCreateFileDialog = true
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(PyCardBorder)),
                            modifier = Modifier.testTag("create_new_file_button"),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = PyCyan, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("New Script", color = TerminalText, fontSize = 13.sp)
                        }
                    }
                }

                // 3. FILES HEADER & BATCH CONTROLS
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Stored Scripts (${files.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TerminalText
                        )

                        Row {
                            if (!isMultiSelect) {
                                TextButton(
                                    onClick = { viewModel.selectAllFiles() },
                                    modifier = Modifier.testTag("enter_select_mode_button")
                                ) {
                                    Text("Select", color = PyCyan, fontSize = 13.sp)
                                }
                            } else {
                                TextButton(
                                    onClick = {
                                        if (selectedIds.size == files.size) viewModel.clearSelection()
                                        else viewModel.selectAllFiles()
                                    }
                                ) {
                                    Text(
                                        text = if (selectedIds.size == files.size) "Deselect All" else "Select All",
                                        color = PyCyan,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. FILE CARDS
                items(files, key = { it.id }) { file ->
                    val isSelected = selectedIds.contains(file.id)
                    val isCurrentlyHostingThis = isHosting && hostingFileName == file.fileName

                    PyFileCard(
                        file = file,
                        isCurrentlyHosting = isCurrentlyHostingThis,
                        isSelected = isSelected,
                        isMultiSelect = isMultiSelect,
                        onHostClick = { viewModel.hostFile(file) },
                        onEditClick = { viewModel.openEditor(file) },
                        onDeleteClick = { fileToDelete = file },
                        onToggleSelect = { viewModel.toggleFileSelection(file.id) },
                        onInstallDepsClick = { viewModel.installAllMissingDeps(file) }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    // Dialogs: Create New File, Delete, Batch Delete, Battery Optimization
    if (showCreateFileDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFileDialog = false },
            title = { Text("Create Python Script", color = TerminalText) },
            text = {
                Column {
                    Text(
                        text = "Enter file name with .py extension:",
                        fontSize = 13.sp,
                        color = TerminalMuted
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newFileNameInput,
                        onValueChange = { newFileNameInput = it },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_file_name_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PyCyan,
                            unfocusedBorderColor = PyCardBorder,
                            focusedTextColor = TerminalText,
                            unfocusedTextColor = TerminalText
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFileNameInput.isNotBlank()) {
                            viewModel.createNewFile(newFileNameInput.trim())
                            showCreateFileDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PyCyan),
                    modifier = Modifier.testTag("confirm_create_file_button")
                ) {
                    Text("Create & Edit", color = PyDarkBackground, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFileDialog = false }) {
                    Text("Cancel", color = TerminalMuted)
                }
            },
            containerColor = PyDarkSurface
        )
    }

    fileToDelete?.let { file ->
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            title = { Text("Delete File", color = TerminalText) },
            text = {
                Text("Are you sure you want to delete '${file.fileName}'? This cannot be undone.", color = TerminalMuted)
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteFile(file)
                        fileToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PyRed),
                    modifier = Modifier.testTag("confirm_delete_button")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToDelete = null }) {
                    Text("Cancel", color = TerminalMuted)
                }
            },
            containerColor = PyDarkSurface
        )
    }

    if (showBatchDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteDialog = false },
            title = { Text("Delete Selected Files", color = TerminalText) },
            text = {
                Text("Delete ${selectedIds.size} selected Python files?", color = TerminalMuted)
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSelectedFiles()
                        showBatchDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PyRed),
                    modifier = Modifier.testTag("confirm_batch_delete_button")
                ) {
                    Text("Delete All Selected")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteDialog = false }) {
                    Text("Cancel", color = TerminalMuted)
                }
            },
            containerColor = PyDarkSurface
        )
    }

    if (showBatteryDialog) {
        AlertDialog(
            onDismissRequest = { showBatteryDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.BatteryChargingFull, contentDescription = null, tint = PyGreen)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("24/7 Background Running", color = TerminalText)
                }
            },
            text = {
                Column {
                    Text(
                        text = "Android battery savers may suspend Telegram Bot sockets when the screen is off or app is minimized.\n\nTo ensure your bot and sockets remain online 24/7 without interruption, disable battery optimization for PyHost.",
                        fontSize = 13.sp,
                        color = TerminalMuted
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(context)
                        showBatteryDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PyGreen)
                ) {
                    Text("Disable Battery Restriction", color = PyDarkBackground, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatteryDialog = false }) {
                    Text("Later", color = TerminalMuted)
                }
            },
            containerColor = PyDarkSurface
        )
    }
}

@Composable
fun PyFileCard(
    file: PyFileEntity,
    isCurrentlyHosting: Boolean,
    isSelected: Boolean,
    isMultiSelect: Boolean,
    onHostClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onToggleSelect: () -> Unit,
    onInstallDepsClick: () -> Unit
) {
    val dateStr = remember(file.lastModified) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(file.lastModified))
    }
    val pkgs = remember(file.detectedPackages) {
        file.getPackageList()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isCurrentlyHosting) PyGreen else if (isSelected) PyCyan else PyCardBorder,
                RoundedCornerShape(14.dp)
            )
            .clickable {
                if (isMultiSelect) {
                    onToggleSelect()
                } else {
                    onEditClick()
                }
            }
            .testTag("file_card_${file.fileName}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) PyDarkSurfaceVariant else PyDarkSurface
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isMultiSelect) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onToggleSelect() },
                            colors = CheckboxDefaults.colors(
                                checkedColor = PyCyan,
                                uncheckedColor = TerminalMuted
                            ),
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = PyCyan.copy(alpha = 0.15f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "py",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = PyCyan,
                                fontSize = 14.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = file.fileName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = TerminalText,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "${file.sizeBytes / 1024 + 1} KB • $dateStr",
                            style = MaterialTheme.typography.bodySmall,
                            color = TerminalMuted,
                            fontSize = 11.sp
                        )
                    }
                }

                if (isCurrentlyHosting) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = PyGreen.copy(alpha = 0.2f),
                        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(PyGreen))
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

            if (pkgs.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Imports: ",
                        fontSize = 11.sp,
                        color = TerminalMuted
                    )
                    pkgs.take(4).forEach { pkg ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = PyDarkSurfaceVariant,
                            border = CardDefaults.outlinedCardBorder(),
                            modifier = Modifier.padding(horizontal = 2.dp)
                        ) {
                            Text(
                                text = pkg,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = PyCyan,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                    if (pkgs.size > 4) {
                        Text(
                            text = "+${pkgs.size - 4}",
                            fontSize = 10.sp,
                            color = TerminalMuted,
                            modifier = Modifier.padding(start = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onHostClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PyGreen,
                        contentColor = PyDarkBackground
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("host_file_${file.fileName}")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "HOST NOW",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = onEditClick,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TerminalText),
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(PyCardBorder)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("edit_file_${file.fileName}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = PyCyan
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Edit", fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("delete_file_${file.fileName}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete",
                            tint = PyRed.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
