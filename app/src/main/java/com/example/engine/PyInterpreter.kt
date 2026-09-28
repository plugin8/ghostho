package com.example.engine

import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class PyMessage(
    val messageId: Long,
    val chatId: Long,
    val text: String,
    val fromUsername: String,
    val fromFirstName: String,
    val fromId: Long,
    val date: Long
)

data class PyBotHandler(
    val commands: List<String>?,
    val isCatchAll: Boolean,
    val functionName: String,
    val bodyLines: List<String>
)

class PyTeleBot(
    val token: String,
    val client: OkHttpClient,
    val logCallback: (String, LogType) -> Unit,
    val onBotAuth: (String, String) -> Unit
) {
    val handlers = mutableListOf<PyBotHandler>()
    var botUsername: String? = null
    var botFirstName: String? = null

    fun authenticate(): Boolean {
        return try {
            val request = Request.Builder()
                .url("https://api.telegram.org/bot$token/getMe")
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = JSONObject(response.body?.string() ?: "{}")
                    if (body.optBoolean("ok")) {
                        val res = body.getJSONObject("result")
                        botUsername = res.optString("username", "Bot")
                        botFirstName = res.optString("first_name", "")
                        onBotAuth("@$botUsername", botFirstName ?: "")
                        logCallback("✅ Telegram Bot authenticated: @$botUsername ($botFirstName)", LogType.SUCCESS)
                        true
                    } else {
                        logCallback("⚠️ Telegram auth message: ${body.optString("description")}", LogType.WARN)
                        false
                    }
                } else {
                    logCallback("⚠️ Telegram auth failed (HTTP ${response.code})", LogType.WARN)
                    false
                }
            }
        } catch (e: Exception) {
            logCallback("Telegram connect error: ${e.message}", LogType.WARN)
            false
        }
    }

    fun sendMessage(chatId: Long, text: String, replyToMessageId: Long? = null): Boolean {
        return try {
            val json = JSONObject()
            json.put("chat_id", chatId)
            json.put("text", text)
            if (replyToMessageId != null) {
                json.put("reply_to_message_id", replyToMessageId)
            }

            val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url("https://api.telegram.org/bot$token/sendMessage")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    logCallback("📤 [Reply to $chatId]: \"$text\"", LogType.TELEGRAM)
                    true
                } else {
                    logCallback("⚠️ Failed to send message: HTTP ${response.code}", LogType.WARN)
                    false
                }
            }
        } catch (e: Exception) {
            logCallback("⚠️ SendMessage error: ${e.message}", LogType.STDERR)
            false
        }
    }
}

class PyInterpreter(
    val logCallback: (String, LogType) -> Unit,
    val onBotAuth: (String, String) -> Unit,
    val onUpdateProcessed: () -> Unit
) {
    val isRunning = AtomicBoolean(false)
    val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    var activeBot: PyTeleBot? = null
    val variables = mutableMapOf<String, Any?>()
    val customFunctions = mutableMapOf<String, Pair<List<String>, List<String>>>() // name -> (params, bodyLines)

    fun stop() {
        isRunning.set(false)
        activeBot = null
    }

    suspend fun execute(script: String) = withContext(Dispatchers.IO) {
        isRunning.set(true)
        variables.clear()
        customFunctions.clear()
        activeBot = null

        // Standard Python environment
        variables["__name__"] = "__main__"
        variables["True"] = true
        variables["False"] = false
        variables["None"] = null

        val rawLines = script.lines()

        try {
            // First pass: detect TeleBot tokens anywhere in code
            detectAndSetupTelebot(script)

            // Second pass: scan function definitions & bot decorators
            parseFunctionsAndHandlers(rawLines)

            // Third pass: execute statements recursively (handling if, while, for, try, calls)
            executeBlock(rawLines, 0, variables)

            // If a bot polling loop was triggered or bot is configured, maintain long-polling
            if (activeBot != null && isRunning.get()) {
                runBotPollingLoop(activeBot!!)
            } else if (isRunning.get()) {
                logCallback("✅ Python script completed execution", LogType.SUCCESS)
            }

        } catch (e: CancellationException) {
            logCallback("🛑 Execution cancelled", LogType.WARN)
        } catch (e: Exception) {
            logCallback("❌ Python Runtime Error: ${e.message}", LogType.STDERR)
            e.printStackTrace()
        }
    }

    private fun detectAndSetupTelebot(script: String) {
        val tokenRegexes = listOf(
            Regex("""(?:TeleBot|Bot|token|TOKEN|bot_token|API_TOKEN)\s*[\(=:]\s*["'](\d{8,12}:[A-Za-z0-9_-]{35,})["']"""),
            Regex("""["'](\d{8,12}:[A-Za-z0-9_-]{35,})["']""")
        )

        var foundToken: String? = null
        for (r in tokenRegexes) {
            r.find(script)?.let {
                foundToken = it.groupValues[1]
                return@let
            }
            if (foundToken != null) break
        }

        if (foundToken != null) {
            val bot = PyTeleBot(foundToken!!, client, logCallback, onBotAuth)
            bot.authenticate()
            activeBot = bot
            variables["bot"] = bot
        }
    }

    private fun parseFunctionsAndHandlers(lines: List<String>) {
        var i = 0
        var pendingDecorator: String? = null

        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            if (trimmed.startsWith("@bot.message_handler") || trimmed.startsWith("@dp.message")) {
                pendingDecorator = trimmed
                i++
                continue
            }

            if (trimmed.startsWith("def ")) {
                val header = trimmed.removePrefix("def ").removeSuffix(":")
                val parenIdx = header.indexOf('(')
                if (parenIdx != -1) {
                    val fnName = header.substring(0, parenIdx).trim()
                    val paramsStr = header.substring(parenIdx + 1, header.lastIndexOf(')')).trim()
                    val params = if (paramsStr.isEmpty()) emptyList() else paramsStr.split(",").map { it.trim() }

                    val body = mutableListOf<String>()
                    val baseIndent = getIndent(line)
                    i++
                    while (i < lines.size && (lines[i].isBlank() || getIndent(lines[i]) > baseIndent)) {
                        body.add(lines[i])
                        i++
                    }

                    customFunctions[fnName] = Pair(params, body)

                    if (pendingDecorator != null && activeBot != null) {
                        registerTelegramHandler(pendingDecorator, fnName, body)
                        pendingDecorator = null
                    }
                    continue
                }
            }

            i++
        }
    }

    private fun registerTelegramHandler(decoratorStr: String, fnName: String, body: List<String>) {
        val bot = activeBot ?: return
        var commands: List<String>? = null
        var isCatchAll = false

        if (decoratorStr.contains("commands=")) {
            val match = Regex("""commands\s*=\s*\[([^\]]+)\]""").find(decoratorStr)
            if (match != null) {
                val listStr = match.groupValues[1]
                commands = listStr.split(",").map { it.trim().removeSurrounding("\"").removeSurrounding("'").lowercase() }
            }
        } else {
            isCatchAll = true
        }

        bot.handlers.add(PyBotHandler(commands, isCatchAll, fnName, body))
        val cmdLabel = if (commands != null) commands.joinToString(", ") { "/$it" } else "all messages"
        logCallback("📋 Bot Handler: $fnName for [$cmdLabel]", LogType.INFO)
    }

    /**
     * Executes a block of lines recursively, respecting indentation, if, while, for, try blocks
     */
    private suspend fun executeBlock(lines: List<String>, minIndent: Int, scope: MutableMap<String, Any?>) {
        var i = 0
        while (i < lines.size && isRunning.get()) {
            val rawLine = lines[i]
            val indent = getIndent(rawLine)
            val trimmed = rawLine.trim()

            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("@")) {
                i++
                continue
            }

            // Skip def blocks at top level (already registered)
            if (trimmed.startsWith("def ")) {
                val baseIndent = indent
                i++
                while (i < lines.size && (lines[i].isBlank() || getIndent(lines[i]) > baseIndent)) {
                    i++
                }
                continue
            }

            // 1. IF statement: if condition:
            if (trimmed.startsWith("if ") && trimmed.endsWith(":")) {
                val conditionStr = trimmed.removePrefix("if ").removeSuffix(":").trim()
                val ifBody = mutableListOf<String>()
                val baseIndent = indent
                i++
                while (i < lines.size && (lines[i].isBlank() || getIndent(lines[i]) > baseIndent)) {
                    ifBody.add(lines[i])
                    i++
                }

                // Check condition (e.g. __name__ == '__main__' is TRUE)
                val condVal = evalCondition(conditionStr, scope)
                if (condVal) {
                    executeBlock(ifBody, baseIndent + 1, scope)
                }
                continue
            }

            // 2. WHILE statement: while condition:
            if (trimmed.startsWith("while ") && trimmed.endsWith(":")) {
                val conditionStr = trimmed.removePrefix("while ").removeSuffix(":").trim()
                val whileBody = mutableListOf<String>()
                val baseIndent = indent
                i++
                while (i < lines.size && (lines[i].isBlank() || getIndent(lines[i]) > baseIndent)) {
                    whileBody.add(lines[i])
                    i++
                }

                while (isRunning.get()) {
                    val condVal = evalCondition(conditionStr, scope)
                    if (!condVal) break

                    executeBlock(whileBody, baseIndent + 1, scope)
                    delay(50) // prevent spin lock
                }
                continue
            }

            // 3. FOR statement: for x in iterable:
            if (trimmed.startsWith("for ") && trimmed.contains(" in ") && trimmed.endsWith(":")) {
                val header = trimmed.removePrefix("for ").removeSuffix(":")
                val parts = header.split(" in ", limit = 2)
                val varName = parts[0].trim()
                val iterableStr = parts[1].trim()

                val forBody = mutableListOf<String>()
                val baseIndent = indent
                i++
                while (i < lines.size && (lines[i].isBlank() || getIndent(lines[i]) > baseIndent)) {
                    forBody.add(lines[i])
                    i++
                }

                val items = getIterableItems(iterableStr, scope)
                for (item in items) {
                    if (!isRunning.get()) break
                    scope[varName] = item
                    executeBlock(forBody, baseIndent + 1, scope)
                }
                continue
            }

            // 4. TRY / EXCEPT
            if (trimmed == "try:") {
                val tryBody = mutableListOf<String>()
                val baseIndent = indent
                i++
                while (i < lines.size && (lines[i].isBlank() || getIndent(lines[i]) > baseIndent)) {
                    tryBody.add(lines[i])
                    i++
                }
                // Skip except lines
                if (i < lines.size && lines[i].trim().startsWith("except")) {
                    val exceptIndent = getIndent(lines[i])
                    i++
                    while (i < lines.size && (lines[i].isBlank() || getIndent(lines[i]) > exceptIndent)) {
                        i++
                    }
                }
                try {
                    executeBlock(tryBody, baseIndent + 1, scope)
                } catch (e: Exception) {
                    logCallback("Handled exception in try block: ${e.message}", LogType.WARN)
                }
                continue
            }

            // 5. Normal statement execution
            executeStatement(trimmed, scope)
            i++
        }
    }

    private suspend fun executeStatement(stmt: String, scope: MutableMap<String, Any?>) {
        val trimmed = stmt.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return

        // 1. print(...) statement
        if (trimmed.startsWith("print(") && trimmed.endsWith(")")) {
            val inner = trimmed.substring(6, trimmed.length - 1).trim()
            val args = splitArguments(inner)
            val output = args.joinToString(" ") { evalExpr(it, scope).toString() }
            logCallback(output, LogType.STDOUT)
            return
        }

        // 2. time.sleep(...)
        if (trimmed.startsWith("time.sleep(") && trimmed.endsWith(")")) {
            val secStr = trimmed.substring(11, trimmed.length - 1).trim()
            val sec = evalExpr(secStr, scope).toString().toDoubleOrNull() ?: 1.0
            delay((sec * 1000).toLong())
            return
        }

        // 3. User function call e.g. main()
        val callMatch = Regex("""^([a-zA-Z0-9_]+)\(\s*\)$""").find(trimmed)
        if (callMatch != null) {
            val fnName = callMatch.groupValues[1]
            if (customFunctions.containsKey(fnName)) {
                val (_, body) = customFunctions[fnName]!!
                executeBlock(body, 0, scope)
                return
            }
        }

        // 4. bot.polling() or bot.infinity_polling()
        if (trimmed.contains("bot.polling") || trimmed.contains("bot.infinity_polling") || trimmed.contains("app.run_polling")) {
            if (activeBot != null) {
                runBotPollingLoop(activeBot!!)
            }
            return
        }

        // 5. Variable assignment e.g. x = 10, bot = TeleBot(...)
        if (trimmed.contains("=") && !trimmed.contains("==") && !trimmed.contains("<=") && !trimmed.contains(">=")) {
            val parts = trimmed.split("=", limit = 2)
            val varName = parts[0].trim()
            val valExpr = parts[1].trim()
            val result = evalExpr(valExpr, scope)
            scope[varName] = result
            return
        }

        // 6. Direct expressions e.g. requests.get(...)
        evalExpr(trimmed, scope)
    }

    private fun evalCondition(conditionStr: String, scope: Map<String, Any?>): Boolean {
        val cond = conditionStr.trim()

        // Handle `__name__ == "__main__"` or `__name__ == '__main__'`
        if (cond.contains("__name__") && (cond.contains("__main__"))) {
            return true
        }

        if (cond == "True") return true
        if (cond == "False") return false

        if (cond.contains(" == ")) {
            val parts = cond.split(" == ")
            return evalExpr(parts[0], scope).toString() == evalExpr(parts[1], scope).toString()
        }
        if (cond.contains(" != ")) {
            val parts = cond.split(" != ")
            return evalExpr(parts[0], scope).toString() != evalExpr(parts[1], scope).toString()
        }

        val res = evalExpr(cond, scope)
        return when (res) {
            is Boolean -> res
            is Number -> res.toDouble() != 0.0
            is String -> res.isNotEmpty() && res != "False"
            else -> res != null
        }
    }

    private fun getIterableItems(iterableStr: String, scope: Map<String, Any?>): List<Any?> {
        val trimmed = iterableStr.trim()
        if (trimmed.startsWith("range(") && trimmed.endsWith(")")) {
            val inner = trimmed.substring(6, trimmed.length - 1).trim()
            val args = inner.split(",").map { evalExpr(it.trim(), scope).toString().toIntOrNull() ?: 0 }
            val list = mutableListOf<Any?>()
            when (args.size) {
                1 -> for (n in 0 until args[0]) list.add(n)
                2 -> for (n in args[0] until args[1]) list.add(n)
                3 -> for (n in args[0] until args[1] step args[2]) list.add(n)
            }
            return list
        }
        val evaluated = evalExpr(trimmed, scope)
        if (evaluated is List<*>) {
            return evaluated
        }
        return emptyList()
    }

    private fun evalExpr(expr: String, scope: Map<String, Any?>): Any? {
        val trimmed = expr.trim()

        // Strings
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) || (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            return trimmed.substring(1, trimmed.length - 1)
        }

        // F-strings: f"Hello {var}"
        if (trimmed.startsWith("f\"") && trimmed.endsWith("\"")) {
            var template = trimmed.substring(2, trimmed.length - 1)
            val varMatches = Regex("""\{([^}]+)\}""").findAll(template)
            for (m in varMatches) {
                val varKey = m.groupValues[1].trim()
                val varVal = evalExpr(varKey, scope)
                template = template.replace(m.value, varVal.toString())
            }
            return template
        }

        // Numbers
        trimmed.toIntOrNull()?.let { return it }
        trimmed.toDoubleOrNull()?.let { return it }

        if (trimmed == "True") return true
        if (trimmed == "False") return false
        if (trimmed == "None") return null

        // requests.get(...)
        if (trimmed.startsWith("requests.get(") && trimmed.endsWith(")")) {
            val urlArg = trimmed.substring(13, trimmed.length - 1).trim()
            val url = evalExpr(urlArg, scope).toString()
            return try {
                val req = Request.Builder().url(url).build()
                client.newCall(req).execute().use { resp ->
                    resp.body?.string() ?: ""
                }
            } catch (e: Exception) {
                "Error: ${e.message}"
            }
        }

        // time functions
        if (trimmed == "time.time()") return System.currentTimeMillis() / 1000.0
        if (trimmed == "time.ctime()") return SimpleDateFormat("EEE MMM d HH:mm:ss yyyy", Locale.US).format(Date())

        // Scope lookup
        if (scope.containsKey(trimmed)) {
            return scope[trimmed]
        }
        if (variables.containsKey(trimmed)) {
            return variables[trimmed]
        }

        return trimmed
    }

    private fun splitArguments(argsStr: String): List<String> {
        val result = mutableListOf<String>()
        var inQuotes = false
        var quoteChar = ' '
        val sb = StringBuilder()

        for (c in argsStr) {
            if (!inQuotes && (c == '"' || c == '\'')) {
                inQuotes = true
                quoteChar = c
                sb.append(c)
            } else if (inQuotes && c == quoteChar) {
                inQuotes = false
                sb.append(c)
            } else if (!inQuotes && c == ',') {
                result.add(sb.toString().trim())
                sb.clear()
            } else {
                sb.append(c)
            }
        }
        if (sb.isNotBlank()) {
            result.add(sb.toString().trim())
        }
        return result
    }

    private suspend fun runBotPollingLoop(bot: PyTeleBot) {
        logCallback("🌐 Telegram Bot Long-Polling Loop Started (24/7 Active)", LogType.SUCCESS)
        var lastUpdateId = 0L

        while (isRunning.get()) {
            try {
                val url = "https://api.telegram.org/bot${bot.token}/getUpdates?offset=$lastUpdateId&timeout=20"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "PyHost-Android/2.4")
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: ""
                    val json = JSONObject(bodyStr)
                    if (json.optBoolean("ok")) {
                        val result = json.getJSONArray("result")
                        for (idx in 0 until result.length()) {
                            val update = result.getJSONObject(idx)
                            val updateId = update.getLong("update_id")
                            lastUpdateId = updateId + 1
                            onUpdateProcessed()

                            handleTelegramUpdate(bot, update)
                        }
                    }
                } else {
                    delay(2500)
                }
            } catch (e: Exception) {
                if (isRunning.get()) {
                    delay(3000)
                }
            }
        }
    }

    private suspend fun handleTelegramUpdate(bot: PyTeleBot, update: JSONObject) {
        val msgObj = update.optJSONObject("message") ?: return
        val chatObj = msgObj.getJSONObject("chat")
        val chatId = chatObj.getLong("id")
        val text = msgObj.optString("text", "")
        val fromObj = msgObj.optJSONObject("from")
        val userHandle = fromObj?.optString("username") ?: fromObj?.optString("first_name", "User") ?: "User"

        logCallback("📥 [@$userHandle]: \"$text\"", LogType.TELEGRAM)

        val pyMsg = PyMessage(
            messageId = msgObj.optLong("message_id", 0),
            chatId = chatId,
            text = text,
            fromUsername = userHandle,
            fromFirstName = fromObj?.optString("first_name", "") ?: "",
            fromId = fromObj?.optLong("id", 0) ?: 0,
            date = msgObj.optLong("date", System.currentTimeMillis() / 1000)
        )

        var handled = false
        val cleanText = text.trim().lowercase()

        for (handler in bot.handlers) {
            var match = false
            if (handler.commands != null) {
                for (cmd in handler.commands) {
                    val commandName = cmd.removePrefix("/")
                    if (cleanText == "/$commandName" || cleanText.startsWith("/$commandName ")) {
                        match = true
                        break
                    }
                }
            } else if (handler.isCatchAll) {
                match = true
            }

            if (match) {
                handled = true
                executeHandlerBody(bot, handler.bodyLines, pyMsg)
                break
            }
        }

        if (!handled) {
            if (cleanText == "/start") {
                bot.sendMessage(chatId, "Hello! I am running via PyHost Android Daemon.\nSend messages or /help.", pyMsg.messageId)
            } else if (cleanText == "/help") {
                bot.sendMessage(chatId, "PyHost Bot is online.\nBackground sockets and long-polling active 24/7.", pyMsg.messageId)
            }
        }
    }

    private suspend fun executeHandlerBody(bot: PyTeleBot, bodyLines: List<String>, message: PyMessage) {
        val localVars = mutableMapOf<String, Any?>()
        localVars["message"] = message
        localVars["bot"] = bot

        for (line in bodyLines) {
            if (!isRunning.get()) break
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue

            // bot.reply_to(message, text)
            if (trimmed.startsWith("bot.reply_to(") || trimmed.startsWith("bot.send_message(")) {
                val replyText = extractReplyString(trimmed, message, localVars)
                bot.sendMessage(message.chatId, replyText, message.messageId)
                continue
            }

            // print(...)
            if (trimmed.startsWith("print(") && trimmed.endsWith(")")) {
                val inner = trimmed.substring(6, trimmed.length - 1).trim()
                logCallback(evalExpr(inner, localVars).toString(), LogType.STDOUT)
                continue
            }

            if (trimmed.contains("=") && !trimmed.contains("==")) {
                val parts = trimmed.split("=", limit = 2)
                val varName = parts[0].trim()
                localVars[varName] = evalExpr(parts[1].trim(), localVars)
            }
        }
    }

    private fun extractReplyString(stmt: String, message: PyMessage, localVars: Map<String, Any?>): String {
        val parenIdx = stmt.indexOf('(')
        if (parenIdx == -1) return "Message received."
        val argsStr = stmt.substring(parenIdx + 1, stmt.lastIndexOf(')')).trim()

        val parts = argsStr.split(",", limit = 2)
        if (parts.size >= 2) {
            val textArg = parts[1].trim()
            if (textArg == "message.text") {
                return message.text
            }
            return evalExpr(textArg, localVars).toString()
        }
        return "Message acknowledged."
    }

    private fun getIndent(line: String): Int {
        var count = 0
        for (c in line) {
            if (c == ' ') count++
            else if (c == '\t') count += 4
            else break
        }
        return count
    }
}
