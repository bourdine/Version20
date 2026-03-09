package com.lottttto.miner.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.lottttto.miner.MainActivity
import com.lottttto.miner.R
import com.lottttto.miner.repositories.MiningRepositoryImpl
import com.lottttto.miner.utils.BatteryOptimizationHelper
import com.lottttto.miner.utils.NativeMinerLib
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

// ========== FirebaseMessagingService ==========
class FirebaseMessagingService : FirebaseMessagingService() {
    companion object {
        const val CHANNEL_ID = "mining_events"
        const val CHANNEL_NAME = "Mining Events"
    }

    override fun onNewToken(token: String) { Log.d("FCM", "New token: $token") }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        // Обработка уведомлений (можно добавить позже)
    }
}

// ========== RealMiningWorker ==========
class RealMiningWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val nativeMiner = NativeMinerLib()

    override suspend fun doWork(): Result {
        val poolUrl = inputData.getString("pool_url") ?: return Result.failure()
        val walletAddress = inputData.getString("wallet_address") ?: return Result.failure()
        val algo = inputData.getString("algo") ?: return Result.failure()
        val workerName = inputData.getString("worker_name") ?: "worker1"
        val password = inputData.getString("password") ?: "x"

        val started = nativeMiner.startMining(poolUrl, walletAddress, workerName, password, algo, 1)
        if (!started) return Result.failure()

        while (!isStopped) {
            // Здесь можно обновлять статистику через репозиторий, но для этого нужен доступ к нему
            // Пока просто держим процесс
            delay(5000)
        }
        nativeMiner.stopMining()
        return Result.success()
    }
}

// ========== MiningForegroundService ==========
@AndroidEntryPoint
class MiningForegroundService : Service() {

    @Inject lateinit var nativeMinerLib: NativeMinerLib

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    private var isRunning = false
    private var miningJob: Job? = null
    private var poolUrl = ""
    private var walletAddress = ""
    private var workerName = ""
    private var algo = ""

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "mining_channel"

        fun start(context: Context, poolUrl: String, walletAddress: String, workerName: String, algo: String) {
            val intent = Intent(context, MiningForegroundService::class.java).apply {
                putExtra("poolUrl", poolUrl); putExtra("walletAddress", walletAddress); putExtra("workerName", workerName); putExtra("algo", algo)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun stop(context: Context) { context.stopService(Intent(context, MiningForegroundService::class.java)) }
    }

    override fun onCreate() { super.onCreate(); createNotificationChannel(); acquireWakeLock() }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (isRunning) return START_STICKY
        poolUrl = intent?.getStringExtra("poolUrl") ?: ""
        walletAddress = intent?.getStringExtra("walletAddress") ?: ""
        workerName = intent?.getStringExtra("workerName") ?: "worker1"
        algo = intent?.getStringExtra("algo") ?: "rx/0"
        startForeground(NOTIFICATION_ID, createNotification("Mining started", "0 H/s"))
        startMining()
        return START_STICKY
    }

    private fun startMining() {
        isRunning = true
        miningJob = serviceScope.launch {
            nativeMinerLib.startMining(poolUrl, walletAddress, workerName, "x", algo, 1)
            while (isRunning) {
                if (BatteryOptimizationHelper.getBatteryLevel(this@MiningForegroundService) < 15) stopMining("Low battery")
                if (BatteryOptimizationHelper.isOverheated(this@MiningForegroundService, 45f)) stopMining("Overheat")
                updateNotification(nativeMinerLib.getHashrate(), nativeMinerLib.getAcceptedShares(), nativeMinerLib.getRejectedShares())
                wakeLock?.acquire(10 * 60 * 1000L)
                delay(5000)
            }
        }
    }

    private fun stopMining(reason: String) {
        if (!isRunning) return
        isRunning = false
        nativeMinerLib.stopMining()
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, createNotification("Mining stopped", reason))
        stopSelf()
    }

    private fun updateNotification(hashrate: Double, accepted: Long, rejected: Long) {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, createNotification("%.2f H/s".format(hashrate), "A:$accepted R:$rejected"))
    }

    private fun createNotification(title: String, content: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title).setContentText(content).setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent).setOngoing(true).setPriority(NotificationCompat.PRIORITY_LOW).build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Mining", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Mining process notifications"; setSound(null, null); enableLights(false); enableVibration(false)
            }
            (getSystemService(NotificationManager::class.java)).createNotificationChannel(channel)
        }
    }

    private fun acquireWakeLock() {
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Lottttto:MiningWakeLock").apply { setReferenceCounted(false) }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        miningJob?.cancel()
        serviceScope.cancel()
        nativeMinerLib.stopMining()
        if (wakeLock?.isHeld == true) wakeLock?.release()
    }
}
