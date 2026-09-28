package com.example.engine

import android.content.Context
import com.example.data.pip.PipPackageManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

enum class LogType {
    STDOUT, STDERR, INFO, WARN, TELEGRAM, SUCCESS, NETWORK
}

data class LogEntry(
    val id: Long = System.nanoTime(),
    val timestamp: Long = System.currentTimeMillis(),
    val message: String,
    val type: LogType
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}

class PythonRuntimeEngine private constructor(private val context: Context) {

    private val pipManager = PipPackageManager(context)

    private val _isHosting = MutableStateFlow(false)
    val isHosting: StateFlow<Boolean> = _isHosting.asStateFlow()

    private val _hostingFileName = MutableStateFlow<String?>(null)
    val hostingFileName: StateFlow<String?> = _hostingFileName.asStateFlow()

    private val _startTime = MutableStateFlow(0L)
    val startTime: StateFlow<Long> = _startTime.asStateFlow()

    private val _updatesProcessed = MutableStateFlow(0)
    val updatesProcessed: StateFlow<Int> = _updatesProcessed.asStateFlow()

    private val _socketActive = MutableStateFlow(false)
    val socketActive: StateFlow<Boolean> = _socketActive.asStateFlow()

    private val _botUsername = MutableStateFlow<String?>(null)
    val botUsername: StateFlow<String?> = _botUsername.asStateFlow()

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private var executionJob: Job? = null
    private var nativeInterpreter: PyInterpreter? = null
    private var externalProcess: Process? = null
    private var processWriter: BufferedWriter? = null

    companion object {
        @Volatile
        private var INSTANCE: PythonRuntimeEngine? = null

        fun getInstance(context: Context): PythonRuntimeEngine {
            return INSTANCE ?: synchronized(this) {
                val instance = PythonRuntimeEngine(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }

        val COMMON_PYTHON_PATHS = listOf(
            "/data/data/com.termux/files/usr/bin/python3",
            "/data/data/com.termux/files/usr/bin/python",
            "/system/bin/python3",
            "/system/bin/python",
            "/system/xbin/python3",
            "/data/local/tmp/python3"
        )
    }

    fun addLog(message: String, type: LogType = LogType.STDOUT) {
        val entry = LogEntry(message = message, type = type)
        val current = _logs.value.toMutableList()
        if (current.size > 2500) {
            current.removeAt(0)
        }
        current.add(entry)
        _logs.value = current
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    fun setBotUsername(username: String) {
        _botUsername.value = username
    }

    fun findAvailableSystemPython(): String? {
        for (path in COMMON_PYTHON_PATHS) {
            val file = File(path)
            if (file.exists() && file.canExecute()) return path
        }
        return null
    }

    /**
     * Start Hosting a Python Script
     */
    fun startHosting(fileName: String, scriptCode: String, forceTermux: Boolean = false) {
        stopHosting()

        _isHosting.value = true
        _hostingFileName.value = fileName
        _startTime.value = System.currentTimeMillis()
        _updatesProcessed.value = 0
        _socketActive.value = true
        _botUsername.value = null

        addLog("==========================================", LogType.INFO)
        addLog("🚀 PyHost Background Engine Active", LogType.INFO)
        addLog("▶ Running File: $fileName (${scriptCode.length} chars)", LogType.INFO)

        val deps = pipManager.detectImports(scriptCode)
        if (deps.isNotEmpty()) {
            addLog("📦 Modules detected: ${deps.joinToString(", ")}", LogType.INFO)
        }

        val systemPython = if (forceTermux) findAvailableSystemPython() else null

        executionJob = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            if (systemPython != null) {
                addLog("⚡ Executing with System/Termux Python: $systemPython", LogType.INFO)
                runWithSystemPython(systemPython, fileName, scriptCode)
            } else {
                addLog("🐍 PyHost Real Python Daemon Started", LogType.SUCCESS)
                nativeInterpreter = PyInterpreter(
                    logCallback = { msg, type -> addLog(msg, type) },
                    onBotAuth = { username, _ ->
                        _botUsername.value = username
                    },
                    onUpdateProcessed = {
                        _updatesProcessed.value += 1
                    }
                )
                nativeInterpreter?.execute(scriptCode)
            }
        }
    }

    private suspend fun runWithSystemPython(pythonPath: String, fileName: String, scriptCode: String) {
        withContext(Dispatchers.IO) {
            try {
                val scriptFile = File(context.filesDir, fileName)
                scriptFile.writeText(scriptCode)

                val pb = ProcessBuilder(pythonPath, "-u", scriptFile.absolutePath)
                val env = pb.environment()
                val currentPath = env["PYTHONPATH"] ?: ""
                env["PYTHONPATH"] = "${pipManager.sitePackagesDir.absolutePath}:$currentPath"
                env["PYTHONUNBUFFERED"] = "1"
                pb.directory(context.filesDir)

                val process = pb.start()
                externalProcess = process
                processWriter = BufferedWriter(OutputStreamWriter(process.outputStream))
                _socketActive.value = true

                addLog("🟢 Process running with PID", LogType.SUCCESS)

                val stdoutJob = launch {
                    BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            if (line != null) addLog(line!!, LogType.STDOUT)
                        }
                    }
                }

                val stderrJob = launch {
                    BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            if (line != null) addLog(line!!, LogType.STDERR)
                        }
                    }
                }

                val exitCode = process.waitFor()
                stdoutJob.join()
                stderrJob.join()
                addLog("🔴 Process exited with code: $exitCode", if (exitCode == 0) LogType.INFO else LogType.STDERR)
            } catch (e: Exception) {
                addLog("External process error: ${e.message}", LogType.STDERR)
                addLog("Falling back to PyHost native runner...", LogType.WARN)
                nativeInterpreter = PyInterpreter(
                    logCallback = { msg, type -> addLog(msg, type) },
                    onBotAuth = { username, _ -> _botUsername.value = username },
                    onUpdateProcessed = { _updatesProcessed.value += 1 }
                )
                nativeInterpreter?.execute(scriptCode)
            }
        }
    }

    fun sendStdin(text: String) {
        val writer = processWriter
        if (writer != null && externalProcess?.isAlive == true) {
            try {
                writer.write(text + "\n")
                writer.flush()
                addLog("⌨ [stdin] $text", LogType.INFO)
            } catch (e: Exception) {
                addLog("Failed to write to stdin: ${e.message}", LogType.STDERR)
            }
        } else {
            addLog("⌨ [stdin input]: $text", LogType.INFO)
        }
    }

    fun stopHosting() {
        if (!_isHosting.value) return

        _isHosting.value = false
        _socketActive.value = false
        executionJob?.cancel()
        executionJob = null

        nativeInterpreter?.stop()
        nativeInterpreter = null

        try {
            externalProcess?.destroy()
            externalProcess = null
            processWriter = null
        } catch (e: Exception) {
            e.printStackTrace()
        }

        addLog("🛑 Host stopped. All background sockets closed.", LogType.WARN)
    }
}
