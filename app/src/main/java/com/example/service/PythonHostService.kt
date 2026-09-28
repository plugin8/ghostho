package com.example.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.R
import com.example.engine.PythonRuntimeEngine
import kotlinx.coroutines.*

class PythonHostService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private lateinit var engine: PythonRuntimeEngine
    private var updateNotificationJob: Job? = null

    companion object {
        const val CHANNEL_ID = "pyhost_foreground_channel"
        const val NOTIFICATION_ID = 2026
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_FILE_NAME = "EXTRA_FILE_NAME"
        const val EXTRA_SCRIPT_CODE = "EXTRA_SCRIPT_CODE"

        fun startService(context: Context, fileName: String, scriptCode: String) {
            val intent = Intent(context, PythonHostService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_FILE_NAME, fileName)
                putExtra(EXTRA_SCRIPT_CODE, scriptCode)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, PythonHostService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        engine = PythonRuntimeEngine.getInstance(this)
        createNotificationChannel()
        acquireLocks()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "PyHost Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Python scripts and Telegram Bots running in background"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun acquireLocks() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "PyHost::BackgroundExecutionWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire(24 * 60 * 60 * 1000L) // 24 hours max
            }

            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiLock = wifiManager.createWifiLock(
                WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                "PyHost::PersistentWifiLock"
            ).apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun releaseLocks() {
        try {
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
            wakeLock = null

            wifiLock?.let {
                if (it.isHeld) it.release()
            }
            wifiLock = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        if (action == ACTION_STOP) {
            engine.stopHosting()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val fileName = intent?.getStringExtra(EXTRA_FILE_NAME) ?: "script.py"
        var scriptCode = intent?.getStringExtra(EXTRA_SCRIPT_CODE) ?: ""
        if (scriptCode.isEmpty()) {
            val fileOnDisk = java.io.File(filesDir, fileName)
            if (fileOnDisk.exists()) {
                scriptCode = fileOnDisk.readText()
            }
        }

        startForegroundWithNotification(fileName)

        if (scriptCode.isNotEmpty()) {
            engine.startHosting(fileName, scriptCode)
        }

        startNotificationTicker(fileName)

        return START_STICKY
    }

    private fun startForegroundWithNotification(fileName: String) {
        val notification = buildNotification(fileName, "Initializing Python daemon...")
        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            }
        } else {
            0
        }

        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, fgsType)
    }

    private fun buildNotification(fileName: String, statusText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpenApp = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, PythonHostService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStop = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PyHost • $fileName")
            .setContentText(statusText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(pendingOpenApp)
            .addAction(android.R.drawable.ic_delete, "Stop Host", pendingStop)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun startNotificationTicker(fileName: String) {
        updateNotificationJob?.cancel()
        updateNotificationJob = serviceScope.launch {
            while (isActive && engine.isHosting.value) {
                delay(3000)
                val updates = engine.updatesProcessed.value
                val botUser = engine.botUsername.value
                val statusText = if (botUser != null) {
                    "Bot $botUser online • Updates: $updates • Socket active"
                } else {
                    "Hosting active • Sockets connected"
                }
                val notification = buildNotification(fileName, statusText)
                val manager = getSystemService(NotificationManager::class.java)
                manager.notify(NOTIFICATION_ID, notification)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        updateNotificationJob?.cancel()
        serviceScope.cancel()
        releaseLocks()
        engine.stopHosting()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
