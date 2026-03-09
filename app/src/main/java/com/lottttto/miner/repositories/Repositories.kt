package com.lottttto.miner.repositories

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import androidx.work.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.lottttto.miner.models.*
import com.lottttto.miner.services.RealMiningWorker
import com.lottttto.miner.utils.CryptoPaymentManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

// ========== DataStore ==========
private val Context.dataStore by preferencesDataStore("settings")

// ========== AuthRepository ==========
interface AuthRepository {
    suspend fun register(email: String, password: String): Result<String>
    suspend fun login(email: String, password: String): Result<String>
    suspend fun sendPasswordResetEmail(email: String): Result<Unit>
    suspend fun getCurrentUserId(): String?
    suspend fun isUserLoggedIn(): Boolean
    suspend fun logout()
    suspend fun hasPin(): Boolean
    suspend fun savePin(pinHash: String, userId: String)
    suspend fun checkPin(pinHash: String): Boolean
    suspend fun getPinUserId(): String?
    suspend fun clearPin()
}

@Singleton
class AuthRepositoryImpl @Inject constructor(
    context: Context
) : AuthRepository {

    private val firebaseAuth = Firebase.auth
    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    private val prefs = EncryptedSharedPreferences.create(
        "auth_prefs",
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    override suspend fun register(email: String, password: String): Result<String> = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { cont ->
            firebaseAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        cont.resume(Result.success(firebaseAuth.currentUser?.uid ?: ""))
                    } else {
                        cont.resume(Result.failure(task.exception ?: Exception("Registration failed")))
                    }
                }
        }
    }

    override suspend fun login(email: String, password: String): Result<String> = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { cont ->
            firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        cont.resume(Result.success(firebaseAuth.currentUser?.uid ?: ""))
                    } else {
                        cont.resume(Result.failure(task.exception ?: Exception("Login failed")))
                    }
                }
        }
    }

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { cont ->
            firebaseAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) cont.resume(Result.success(Unit))
                    else cont.resume(Result.failure(task.exception ?: Exception("Failed to send reset email")))
                }
        }
    }

    override suspend fun getCurrentUserId(): String? = firebaseAuth.currentUser?.uid
    override suspend fun isUserLoggedIn(): Boolean = firebaseAuth.currentUser != null

    override suspend fun logout() { firebaseAuth.signOut() }

    override suspend fun hasPin(): Boolean = prefs.contains("pin_hash") && prefs.contains("pin_user_id")

    override suspend fun savePin(pinHash: String, userId: String) {
        prefs.edit().putString("pin_hash", pinHash).putString("pin_user_id", userId).apply()
    }

    override suspend fun checkPin(pinHash: String): Boolean {
        val savedHash = prefs.getString("pin_hash", null)
        val savedUserId = prefs.getString("pin_user_id", null)
        val currentUserId = firebaseAuth.currentUser?.uid
        return savedHash == pinHash && savedUserId == currentUserId
    }

    override suspend fun getPinUserId(): String? = prefs.getString("pin_user_id", null)

    override suspend fun clearPin() { prefs.edit().remove("pin_hash").remove("pin_user_id").apply() }

    companion object {
        fun hashPin(pin: String): String {
            val bytes = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray())
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}

// ========== MiningRepository ==========
interface MiningRepository {
    val miningStats: Flow<MiningStats>
    val isMiningActive: Flow<Boolean>
    suspend fun startMining(coin: CoinType, mode: MiningMode, pool: Pool?, wallet: Wallet)
    suspend fun stopMining()
    fun getAvailablePoolsForCoin(coin: CoinType): List<Pool>
}

@Singleton
class MiningRepositoryImpl @Inject constructor(
    private val workManager: WorkManager,
    private val paymentManager: CryptoPaymentManager
) : MiningRepository {

    private val _miningStats = MutableStateFlow(
        MiningStats(CoinType.MONERO, 0.0, 0, 0, 0.0)
    )
    override val miningStats: Flow<MiningStats> = _miningStats.asStateFlow()

    private val _isMiningActive = MutableStateFlow(false)
    override val isMiningActive: Flow<Boolean> = _isMiningActive.asStateFlow()

    override suspend fun startMining(coin: CoinType, mode: MiningMode, pool: Pool?, wallet: Wallet) {
        val poolUrl = when (mode) {
            MiningMode.POOL -> pool?.url ?: throw IllegalArgumentException("Pool required for pool mining")
            MiningMode.SOLO -> getSoloUrl(coin)
        }
        val algo = coin.getAlgorithm().algoName

        val workRequest = OneTimeWorkRequestBuilder<RealMiningWorker>()
            .setConstraints(Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build())
            .setInputData(
                workDataOf(
                    "pool_url" to poolUrl,
                    "wallet_address" to wallet.address,
                    "algo" to algo,
                    "worker_name" to "lottttto_worker",
                    "password" to "x"
                )
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()

        workManager.enqueueUniqueWork(
            "mining_work_${coin.name}",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )

        _isMiningActive.update { true }
        _miningStats.update { it.copy(coin = coin) }
    }

    override suspend fun stopMining() {
        workManager.cancelUniqueWork("mining_work_${_miningStats.value.coin.name}")
        _isMiningActive.update { false }
        _miningStats.update { it.copy(hashrate = 0.0, acceptedShares = 0, rejectedShares = 0, estimatedEarnings = 0.0) }
    }

    override fun getAvailablePoolsForCoin(coin: CoinType): List<Pool> = emptyList() // будет загружаться из JSON

    private fun getSoloUrl(coin: CoinType): String = when (coin) {
        CoinType.MONERO -> "stratum+tcp://solo.moneroocean.stream:5555"
        CoinType.BITCOIN -> "stratum+tcp://solo.ckpool.org:3333"
        CoinType.LITECOIN -> "stratum+tcp://solo.litecoinpool.org:3333"
        else -> throw IllegalArgumentException("Solo mining not supported for this coin")
    }

    suspend fun updateStats(hashrate: Double, acceptedShares: Long, rejectedShares: Long) {
        val shouldCharge = paymentManager.shouldChargeFee()
        val feeMultiplier = if (shouldCharge) 0.9 else 1.0
        _miningStats.update { current ->
            current.copy(
                hashrate = hashrate,
                acceptedShares = acceptedShares,
                rejectedShares = rejectedShares,
                estimatedEarnings = hashrate * acceptedShares * 1e-12 * feeMultiplier
            )
        }
    }
}

// ========== WalletRepository ==========
interface WalletRepository {
    fun getAllWallets(): Flow<List<Wallet>>
    suspend fun getWalletsForCoin(coin: CoinType): List<Wallet>
    suspend fun addWallet(address: String, coin: CoinType, label: String?, seedPhrase: String?)
    suspend fun updateWallet(wallet: Wallet)
    suspend fun deleteWallet(id: Long)
}

@Singleton
class WalletRepositoryImpl @Inject constructor(
    context: Context
) : WalletRepository {

    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    private val prefs = EncryptedSharedPreferences.create(
        "wallet_prefs",
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    private val gson = com.google.gson.Gson()
    private val walletListType = object : com.google.gson.reflect.TypeToken<List<Wallet>>() {}.type

    override fun getAllWallets(): Flow<List<Wallet>> = flow {
        val json = prefs.getString("wallets", "[]")
        val list: List<Wallet> = gson.fromJson(json, walletListType)
        emit(list)
    }

    override suspend fun getWalletsForCoin(coin: CoinType): List<Wallet> {
        val json = prefs.getString("wallets", "[]")
        val list: List<Wallet> = gson.fromJson(json, walletListType)
        return list.filter { it.coin == coin }
    }

    override suspend fun addWallet(address: String, coin: CoinType, label: String?, seedPhrase: String?) {
        val current = getAllWalletsSync()
        val newWallet = Wallet(
            id = (current.maxOfOrNull { it.id } ?: 0) + 1,
            address = address,
            coin = coin,
            label = label,
            seedPhrase = seedPhrase
        )
        saveWallets(current + newWallet)
    }

    override suspend fun updateWallet(wallet: Wallet) {
        val current = getAllWalletsSync()
        val updated = current.map { if (it.id == wallet.id) wallet else it }
        saveWallets(updated)
    }

    override suspend fun deleteWallet(id: Long) {
        val current = getAllWalletsSync()
        val updated = current.filter { it.id != id }
        saveWallets(updated)
    }

    private fun getAllWalletsSync(): List<Wallet> {
        val json = prefs.getString("wallets", "[]")
        return gson.fromJson(json, walletListType)
    }

    private fun saveWallets(wallets: List<Wallet>) {
        val json = gson.toJson(wallets)
        prefs.edit().putString("wallets", json).apply()
    }
}

// ========== PoolRepository ==========
interface PoolRepository {
    suspend fun getPoolsForCoin(coin: CoinType): List<Pool>
}

@Singleton
class PoolRepositoryImpl @Inject constructor(
    private val context: Context
) : PoolRepository {
    private val gson = com.google.gson.Gson()
    private val poolListType = object : com.google.gson.reflect.TypeToken<List<Pool>>() {}.type

    override suspend fun getPoolsForCoin(coin: CoinType): List<Pool> {
        val json = context.assets.open("pools.json").bufferedReader().use { it.readText() }
        val list: List<Pool> = gson.fromJson(json, poolListType)
        return list.filter { it.coin == coin }
    }
}

// ========== SettingsRepository ==========
interface SettingsRepository {
    suspend fun saveComputingUsage(usage: Int)
    fun getComputingUsage(): Flow<Int>
    suspend fun saveTaskWeights(weights: List<Int>)
    fun getTaskWeights(): Flow<List<Int>>
}

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val context: Context
) : SettingsRepository {
    private val dataStore = context.dataStore

    companion object {
        val COMPUTING_USAGE = intPreferencesKey("computing_usage")
        val TASK_WEIGHTS = stringPreferencesKey("task_weights")
    }

    override suspend fun saveComputingUsage(usage: Int) {
        dataStore.edit { prefs -> prefs[COMPUTING_USAGE] = usage }
    }

    override fun getComputingUsage(): Flow<Int> = dataStore.data.map { prefs ->
        prefs[COMPUTING_USAGE] ?: 15   // Изменено: по умолчанию 15% вместо 50
    }

    override suspend fun saveTaskWeights(weights: List<Int>) {
        val json = weights.joinToString(",")
        dataStore.edit { prefs -> prefs[TASK_WEIGHTS] = json }
    }

    override fun getTaskWeights(): Flow<List<Int>> = dataStore.data.map { prefs ->
        prefs[TASK_WEIGHTS]?.split(",")?.mapNotNull { it.toIntOrNull() } ?: defaultTaskWeights()
    }

    private fun defaultTaskWeights(): List<Int> {
        val weights = MutableList(12) { 0 }
        // Индексы: 0=MONERO_POOL, 1=MONERO_SOLO, 2=BTC_POOL, 3=BTC_SOLO, 4=BCH_POOL, 5=BCH_SOLO,
        // 6=LTC_POOL, 7=LTC_SOLO, 8=DOGE_POOL, 9=DOGE_SOLO, 10=ZEC_POOL, 11=ZEC_SOLO
        weights[0] = 50  // Monero Pool
        weights[1] = 50  // Monero Solo
        return weights
    }
}
