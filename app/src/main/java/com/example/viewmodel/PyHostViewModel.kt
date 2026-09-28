package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.PyDatabase
import com.example.data.model.PyFileEntity
import com.example.data.pip.PipPackageInfo
import com.example.data.pip.PipPackageManager
import com.example.engine.LogEntry
import com.example.engine.PythonRuntimeEngine
import com.example.service.PythonHostService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class Screen {
    FILE_LIST,
    CODE_EDITOR,
    TERMINAL
}

class PyHostViewModel(application: Application) : AndroidViewModel(application) {

    private val db = PyDatabase.getInstance(application)
    private val dao = db.pyFileDao()
    private val pipManager = PipPackageManager(application)
    val engine = PythonRuntimeEngine.getInstance(application)

    // Screen navigation
    private val _currentScreen = MutableStateFlow(Screen.FILE_LIST)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    // File lists from Room
    val files: StateFlow<List<PyFileEntity>> = dao.getAllFiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Selection mode for batch delete
    private val _selectedFileIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedFileIds: StateFlow<Set<Long>> = _selectedFileIds.asStateFlow()

    val isMultiSelectMode: StateFlow<Boolean> = _selectedFileIds.map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Code Editor State
    private val _editingFile = MutableStateFlow<PyFileEntity?>(null)
    val editingFile: StateFlow<PyFileEntity?> = _editingFile.asStateFlow()

    private val _editorContent = MutableStateFlow("")
    val editorContent: StateFlow<String> = _editorContent.asStateFlow()

    private val _isEditorDirty = MutableStateFlow(false)
    val isEditorDirty: StateFlow<Boolean> = _isEditorDirty.asStateFlow()

    // Engine states
    val isHosting: StateFlow<Boolean> = engine.isHosting
    val hostingFileName: StateFlow<String?> = engine.hostingFileName
    val logs: StateFlow<List<LogEntry>> = engine.logs
    val startTime: StateFlow<Long> = engine.startTime
    val updatesProcessed: StateFlow<Int> = engine.updatesProcessed
    val socketActive: StateFlow<Boolean> = engine.socketActive
    val botUsername: StateFlow<String?> = engine.botUsername

    // Pip Package Manager State
    private val _isPipDialogVisible = MutableStateFlow(false)
    val isPipDialogVisible: StateFlow<Boolean> = _isPipDialogVisible.asStateFlow()

    private val _pipSearchQuery = MutableStateFlow("")
    val pipSearchQuery: StateFlow<String> = _pipSearchQuery.asStateFlow()

    private val _pipSearchLoading = MutableStateFlow(false)
    val pipSearchLoading: StateFlow<Boolean> = _pipSearchLoading.asStateFlow()

    private val _pipSearchResult = MutableStateFlow<PipPackageInfo?>(null)
    val pipSearchResult: StateFlow<PipPackageInfo?> = _pipSearchResult.asStateFlow()

    private val _installedPackages = MutableStateFlow<List<PipPackageInfo>>(emptyList())
    val installedPackages: StateFlow<List<PipPackageInfo>> = _installedPackages.asStateFlow()

    private val _pipTerminalLogs = MutableStateFlow<List<String>>(emptyList())
    val pipTerminalLogs: StateFlow<List<String>> = _pipTerminalLogs.asStateFlow()

    private val _isPipInstalling = MutableStateFlow(false)
    val isPipInstalling: StateFlow<Boolean> = _isPipInstalling.asStateFlow()

    init {
        loadInstalledPackages()
    }

    fun navigateTo(screen: Screen) {
        _currentScreen.value = screen
    }

    // --- File Import & Management ---

    fun importPythonFile(name: String, content: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val safeName = if (name.endsWith(".py", ignoreCase = true)) name else "$name.py"
            val fileOnDisk = File(getApplication<Application>().filesDir, safeName)
            fileOnDisk.writeText(content)

            val detected = pipManager.detectImports(content).joinToString(",")
            val entity = PyFileEntity(
                fileName = safeName,
                filePath = fileOnDisk.absolutePath,
                content = content,
                sizeBytes = content.toByteArray().size.toLong(),
                lastModified = System.currentTimeMillis(),
                detectedPackages = detected
            )
            val newId = dao.insertFile(entity)

            withContext(Dispatchers.Main) {
                val savedFile = entity.copy(id = newId)
                _editingFile.value = savedFile
                _editorContent.value = content
                _currentScreen.value = Screen.FILE_LIST
            }
        }
    }

    fun createNewFile(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val cleanName = if (name.endsWith(".py", ignoreCase = true)) name else "$name.py"
            val initialTemplate = """# $cleanName
# Python script created in PyHost

def main():
    print("PyHost is running $cleanName")

if __name__ == "__main__":
    main()
"""
            val fileOnDisk = File(getApplication<Application>().filesDir, cleanName)
            fileOnDisk.writeText(initialTemplate)

            val detected = pipManager.detectImports(initialTemplate).joinToString(",")
            val entity = PyFileEntity(
                fileName = cleanName,
                filePath = fileOnDisk.absolutePath,
                content = initialTemplate,
                sizeBytes = initialTemplate.toByteArray().size.toLong(),
                lastModified = System.currentTimeMillis(),
                detectedPackages = detected
            )
            val id = dao.insertFile(entity)
            withContext(Dispatchers.Main) {
                openEditor(entity.copy(id = id))
            }
        }
    }

    fun openEditor(file: PyFileEntity) {
        _editingFile.value = file
        _editorContent.value = file.content
        _isEditorDirty.value = false
        _currentScreen.value = Screen.CODE_EDITOR
    }

    fun updateEditorContent(newContent: String) {
        _editorContent.value = newContent
        _isEditorDirty.value = newContent != (_editingFile.value?.content ?: "")
    }

    fun saveEditorContent() {
        val file = _editingFile.value ?: return
        val newContent = _editorContent.value
        viewModelScope.launch(Dispatchers.IO) {
            val fileOnDisk = File(file.filePath)
            fileOnDisk.writeText(newContent)

            val detected = pipManager.detectImports(newContent).joinToString(",")
            val updated = file.copy(
                content = newContent,
                sizeBytes = newContent.toByteArray().size.toLong(),
                lastModified = System.currentTimeMillis(),
                detectedPackages = detected
            )
            dao.updateFile(updated)
            withContext(Dispatchers.Main) {
                _editingFile.value = updated
                _isEditorDirty.value = false
            }
        }
    }

    fun deleteFile(file: PyFileEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val fileOnDisk = File(file.filePath)
            if (fileOnDisk.exists()) fileOnDisk.delete()
            dao.deleteFile(file)
            if (_editingFile.value?.id == file.id) {
                withContext(Dispatchers.Main) {
                    _editingFile.value = null
                    _currentScreen.value = Screen.FILE_LIST
                }
            }
        }
    }

    fun toggleFileSelection(id: Long) {
        val current = _selectedFileIds.value.toMutableSet()
        if (current.contains(id)) {
            current.remove(id)
        } else {
            current.add(id)
        }
        _selectedFileIds.value = current
    }

    fun selectAllFiles() {
        val allIds = files.value.map { it.id }.toSet()
        _selectedFileIds.value = allIds
    }

    fun clearSelection() {
        _selectedFileIds.value = emptySet()
    }

    fun deleteSelectedFiles() {
        val idsToDelete = _selectedFileIds.value.toList()
        if (idsToDelete.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            val allFiles = files.value
            for (f in allFiles) {
                if (idsToDelete.contains(f.id)) {
                    val fileOnDisk = File(f.filePath)
                    if (fileOnDisk.exists()) fileOnDisk.delete()
                }
            }
            dao.deleteFilesByIds(idsToDelete)
            withContext(Dispatchers.Main) {
                clearSelection()
            }
        }
    }

    // --- Hosting and Execution ---

    fun hostFile(file: PyFileEntity) {
        viewModelScope.launch {
            dao.markLastHosted(file.id)
            PythonHostService.startService(
                context = getApplication(),
                fileName = file.fileName,
                scriptCode = file.content
            )
            _currentScreen.value = Screen.TERMINAL
        }
    }

    fun hostCurrentEditorFile() {
        val file = _editingFile.value ?: return
        val currentContent = _editorContent.value
        viewModelScope.launch {
            // Auto save before hosting
            saveEditorContent()
            PythonHostService.startService(
                context = getApplication(),
                fileName = file.fileName,
                scriptCode = currentContent
            )
            _currentScreen.value = Screen.TERMINAL
        }
    }

    fun stopHosting() {
        PythonHostService.stopService(getApplication())
    }

    fun restartHosting() {
        val name = hostingFileName.value ?: return
        val currentFiles = files.value
        val file = currentFiles.find { it.fileName == name } ?: _editingFile.value
        if (file != null) {
            hostFile(file)
        }
    }

    fun sendStdin(text: String) {
        engine.sendStdin(text)
    }

    fun clearLogs() {
        engine.clearLogs()
    }

    // --- Pip Package Manager ---

    fun openPipDialog() {
        _isPipDialogVisible.value = true
        loadInstalledPackages()
    }

    fun closePipDialog() {
        _isPipDialogVisible.value = false
    }

    fun updatePipSearchQuery(q: String) {
        _pipSearchQuery.value = q
    }

    fun searchPipPackage(packageName: String) {
        if (packageName.isBlank()) return
        viewModelScope.launch {
            _pipSearchLoading.value = true
            val info = pipManager.fetchPyPiInfo(packageName.trim())
            _pipSearchResult.value = info
            _pipSearchLoading.value = false
        }
    }

    fun loadInstalledPackages() {
        viewModelScope.launch {
            _installedPackages.value = pipManager.getInstalledPackages()
        }
    }

    fun installPipPackage(packageName: String) {
        viewModelScope.launch {
            _isPipInstalling.value = true
            appendPipLog("pip install $packageName")
            val success = pipManager.installPackage(packageName) { logLine ->
                appendPipLog(logLine)
                engine.addLog(logLine, com.example.engine.LogType.INFO)
            }
            if (success) {
                appendPipLog("Successfully installed $packageName")
            } else {
                appendPipLog("Failed to install $packageName")
            }
            loadInstalledPackages()
            _isPipInstalling.value = false
        }
    }

    fun uninstallPipPackage(packageName: String) {
        viewModelScope.launch {
            appendPipLog("pip uninstall $packageName")
            val ok = pipManager.uninstallPackage(packageName)
            if (ok) {
                appendPipLog("Successfully removed $packageName")
            } else {
                appendPipLog("Failed to remove $packageName")
            }
            loadInstalledPackages()
        }
    }

    fun installAllMissingDeps(file: PyFileEntity) {
        val pkgs = file.getPackageList()
        viewModelScope.launch {
            _isPipInstalling.value = true
            openPipDialog()
            for (pkg in pkgs) {
                if (!pipManager.isPackageInstalled(pkg)) {
                    appendPipLog("Auto-installing missing dependency: $pkg")
                    pipManager.installPackage(pkg) { logLine ->
                        appendPipLog(logLine)
                    }
                }
            }
            loadInstalledPackages()
            _isPipInstalling.value = false
        }
    }

    private fun appendPipLog(line: String) {
        val current = _pipTerminalLogs.value.toMutableList()
        current.add(line)
        _pipTerminalLogs.value = current
    }

    fun clearPipLogs() {
        _pipTerminalLogs.value = emptyList()
    }
}
