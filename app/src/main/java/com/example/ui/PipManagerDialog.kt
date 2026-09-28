package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.*
import com.example.viewmodel.PyHostViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PipManagerDialog(
    viewModel: PyHostViewModel,
    onDismiss: () -> Unit
) {
    val searchQuery by viewModel.pipSearchQuery.collectAsState()
    val isSearching by viewModel.pipSearchLoading.collectAsState()
    val searchResult by viewModel.pipSearchResult.collectAsState()
    val installedPackages by viewModel.installedPackages.collectAsState()
    val pipLogs by viewModel.pipTerminalLogs.collectAsState()
    val isInstalling by viewModel.isPipInstalling.collectAsState()
    val editingFile by viewModel.editingFile.collectAsState()

    val detectedDeps = remember(editingFile) {
        editingFile?.getPackageList() ?: emptyList()
    }

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Detected & Search, 1: Installed, 2: Pip Console

    val logListState = rememberLazyListState()
    LaunchedEffect(pipLogs.size) {
        if (pipLogs.isNotEmpty()) {
            logListState.animateScrollToItem(pipLogs.size - 1)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f)
                .testTag("pip_manager_dialog"),
            colors = CardDefaults.cardColors(containerColor = PyDarkSurface),
            shape = RoundedCornerShape(16.dp),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(PyCardBorder))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Extension,
                            contentDescription = null,
                            tint = PyCyan,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "PIP Package Manager",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = TerminalText
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_pip_dialog_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TerminalMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Tabs
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = PyDarkSurfaceVariant,
                    contentColor = PyCyan,
                    modifier = Modifier.border(1.dp, PyCardBorder, RoundedCornerShape(8.dp))
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("PyPI & Auto", fontSize = 13.sp) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Installed (${installedPackages.size})", fontSize = 13.sp) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Pip Console", fontSize = 13.sp) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Tab Content
                when (selectedTab) {
                    0 -> {
                        // Auto detected & PyPI Search
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            if (detectedDeps.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "Detected in Current Script:",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = PyCyan,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                }

                                items(detectedDeps) { dep ->
                                    val isInstalled = installedPackages.any { it.name.equals(dep, ignoreCase = true) }
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        colors = CardDefaults.cardColors(containerColor = PyDarkSurfaceVariant)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = dep,
                                                    fontWeight = FontWeight.Bold,
                                                    color = TerminalText,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                                Text(
                                                    text = if (isInstalled) "✓ Installed in site-packages" else "Not yet installed",
                                                    fontSize = 12.sp,
                                                    color = if (isInstalled) PyGreen else PyYellow
                                                )
                                            }

                                            if (!isInstalled) {
                                                Button(
                                                    onClick = {
                                                        selectedTab = 2
                                                        viewModel.installPipPackage(dep)
                                                    },
                                                    enabled = !isInstalling,
                                                    colors = ButtonDefaults.buttonColors(containerColor = PyGreen),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                                ) {
                                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Install", fontSize = 12.sp)
                                                }
                                            } else {
                                                FilledTonalIconButton(
                                                    onClick = { viewModel.uninstallPipPackage(dep) },
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Uninstall", tint = PyRed, modifier = Modifier.size(18.dp))
                                                }
                                            }
                                        }
                                    }
                                }

                                item {
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }

                            item {
                                Text(
                                    text = "Search & Install from PyPI:",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = TerminalText,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(6.dp))

                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { viewModel.updatePipSearchQuery(it) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("pip_search_input"),
                                    placeholder = { Text("e.g. pyTelegramBotAPI, requests, aiohttp...", color = TerminalMuted, fontSize = 13.sp) },
                                    singleLine = true,
                                    trailingIcon = {
                                        IconButton(
                                            onClick = { viewModel.searchPipPackage(searchQuery) },
                                            modifier = Modifier.testTag("pip_search_submit_button")
                                        ) {
                                            if (isSearching) {
                                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = PyCyan)
                                            } else {
                                                Icon(Icons.Default.Search, contentDescription = "Search", tint = PyCyan)
                                            }
                                        }
                                    },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = PyCyan,
                                        unfocusedBorderColor = PyCardBorder,
                                        focusedTextColor = TerminalText,
                                        unfocusedTextColor = TerminalText
                                    )
                                )

                                Spacer(modifier = Modifier.height(10.dp))
                            }

                            searchResult?.let { res ->
                                item {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, PyCyan, RoundedCornerShape(8.dp)),
                                        colors = CardDefaults.cardColors(containerColor = PyDarkSurfaceVariant)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column {
                                                    Text(
                                                        text = res.name,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 16.sp,
                                                        color = PyCyan
                                                    )
                                                    Text(
                                                        text = "v${res.version}",
                                                        fontSize = 12.sp,
                                                        color = TerminalMuted
                                                    )
                                                }

                                                Button(
                                                    onClick = {
                                                        selectedTab = 2
                                                        viewModel.installPipPackage(res.name)
                                                    },
                                                    enabled = !isInstalling,
                                                    colors = ButtonDefaults.buttonColors(containerColor = PyGreen),
                                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                                ) {
                                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("pip install", fontSize = 12.sp)
                                                }
                                            }

                                            if (res.summary.isNotBlank()) {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(
                                                    text = res.summary,
                                                    fontSize = 12.sp,
                                                    color = TerminalText
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        // Installed packages list
                        if (installedPackages.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No third-party packages installed in site-packages yet.\nUse PyPI tab to install packages.",
                                    color = TerminalMuted,
                                    fontSize = 13.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                items(installedPackages) { pkg ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        colors = CardDefaults.cardColors(containerColor = PyDarkSurfaceVariant)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = pkg.name,
                                                    fontWeight = FontWeight.Bold,
                                                    color = TerminalText,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                                Text(
                                                    text = "Version: ${pkg.version}",
                                                    fontSize = 11.sp,
                                                    color = TerminalMuted
                                                )
                                            }

                                            IconButton(
                                                onClick = { viewModel.uninstallPipPackage(pkg.name) }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Uninstall",
                                                    tint = PyRed
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    2 -> {
                        // Pip Console Log Output
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "pip logs (~/site-packages)",
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = TerminalMuted
                                )
                                TextButton(onClick = { viewModel.clearPipLogs() }) {
                                    Text("Clear", fontSize = 12.sp, color = PyCyan)
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .background(TerminalBackground, RoundedCornerShape(8.dp))
                                    .border(1.dp, PyCardBorder, RoundedCornerShape(8.dp))
                                    .padding(8.dp)
                            ) {
                                if (pipLogs.isEmpty()) {
                                    Text(
                                        text = "$ pip --version\npip 24.0 (PyHost Android Package Manager)\nReady for installation.",
                                        color = TerminalMuted,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp
                                    )
                                } else {
                                    LazyColumn(
                                        state = logListState,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        items(pipLogs) { log ->
                                            val color = when {
                                                log.startsWith("ERROR") || log.contains("failed", ignoreCase = true) -> PyRed
                                                log.startsWith("Successfully") -> PyGreen
                                                log.startsWith("Collecting") || log.startsWith("Downloading") -> PyCyan
                                                else -> TerminalText
                                            }
                                            Text(
                                                text = log,
                                                color = color,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 12.sp,
                                                lineHeight = 16.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
