package com.lottttto.miner.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import com.google.gson.Gson
import com.lottttto.miner.models.CoinType
import com.lottttto.miner.services.FirebaseMessagingService
import kotlinx.coroutines.*
import okhttp3.*
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

// ========== BatteryOptimizationHelper ==========
object BatteryOptimizationHelper {
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun requestIgnoreBatteryOptimizations(activity: android.app.Activity) {
        if (!isIgnoringBatteryOptimizations(activity)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${activity.packageName}"))
            activity.startActivity(intent)
        }
    }

    fun getBatteryLevel(context: Context): Int {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    fun isOverheated(context: Context, threshold: Float = 40.0f): Boolean {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val temperature = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_TEMPERATURE) / 10.0f
        Log.d("Battery", "Temp: $temperature")
        return temperature > threshold
    }
}

// ========== NotificationUtils ==========
object NotificationUtils {
    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                FirebaseMessagingService.CHANNEL_ID,
                FirebaseMessagingService.CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Mining events"
                enableLights(true)
                enableVibration(true)
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}

// ========== NativeMinerLib ==========
class NativeMinerLib {
    companion object {
        init {
            try {
                System.loadLibrary("miner")
                Log.d("NativeMinerLib", "Native library loaded")
            } catch (e: UnsatisfiedLinkError) {
                Log.e("NativeMinerLib", "Failed to load native library", e)
            }
        }
    }

    external fun startMining(poolUrl: String, walletAddress: String, workerName: String, password: String, algo: String, threads: Int): Boolean
    external fun stopMining(): Boolean
    external fun getHashrate(): Double
    external fun getAcceptedShares(): Long
    external fun getRejectedShares(): Long
}

// ========== CryptoPaymentManager ==========
@Singleton
class CryptoPaymentManager @Inject constructor(
    private val context: Context
) {
    private val _premiumStatus = MutableStateFlow(PremiumStatus.INACTIVE)
    val premiumStatus: StateFlow<PremiumStatus> = _premiumStatus.asStateFlow()

    enum class PremiumStatus { ACTIVE, INACTIVE }
    enum class CryptoCurrency(val displayName: String, val symbol: String) {
        BITCOIN("Bitcoin", "BTC"),
        ETHEREUM("Ethereum", "ETH"),
        USDT_ERC20("Tether (ERC-20)", "USDT"),
        LITECOIN("Litecoin", "LTC"),
        DOGECOIN("Dogecoin", "DOGE"),
        MONERO("Monero", "XMR"),
        BITCOIN_CASH("Bitcoin Cash", "BCH"),
        ZCASH("ZCash", "ZEC")
    }

    private val client = OkHttpClient.Builder().connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS).build()
    private val gson = Gson()
    private val API_KEY = "your_plisio_api_key_here"

    suspend fun createInvoice(userId: String, currency: CryptoCurrency, amount: Double = 1.99): Result<InvoiceData> = withContext(Dispatchers.IO) {
        val orderNumber = "SUB_${userId}_${System.currentTimeMillis()}"
        val params = mapOf(
            "api_key" to API_KEY,
            "amount" to amount.toString(),
            "currency" to currency.symbol,
            "order_name" to "Lottttto Premium Monthly",
            "order_number" to orderNumber,
            "description" to "Premium subscription - 0% mining fee",
            "callback_url" to "https://your-server.com/webhook/plisio",
            "success_url" to "lottttto://payment/success"
        )
        val urlBuilder = HttpUrl.Builder().scheme("https").host("api.plisio.net").addPathSegment("api").addPathSegment("v1").addPathSegment("invoices").addPathSegment("new")
        params.forEach { urlBuilder.addQueryParameter(it.key, it.value) }
        val request = Request.Builder().url(urlBuilder.build()).get().build()
        try {
            val response = client.newCall(request).execute()
            val body = response.body?.string()
            if (response.isSuccessful) {
                val json = gson.fromJson(body, Map::class.java)
                if (json["status"] == "success") {
                    val data = json["data"] as Map<*, *>
                    Result.success(InvoiceData(data["invoice_url"] as String, data["invoice_id"] as String))
                } else Result.failure(Exception("Plisio error: $body"))
            } else Result.failure(Exception("HTTP ${response.code}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun openPaymentPage(invoiceUrl: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(invoiceUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun shouldChargeFee(): Boolean = _premiumStatus.value == PremiumStatus.INACTIVE

    fun activatePremium() {
        _premiumStatus.value = PremiumStatus.ACTIVE
    }

    data class InvoiceData(val invoiceUrl: String, val invoiceId: String)
}
