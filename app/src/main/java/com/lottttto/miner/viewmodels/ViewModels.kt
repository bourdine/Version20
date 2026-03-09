package com.lottttto.miner.viewmodels

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lottttto.miner.models.*
import com.lottttto.miner.repositories.*
import com.lottttto.miner.utils.CryptoPaymentManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ========== AuthViewModel ==========
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepositoryImpl
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun checkAuthState() {
        viewModelScope.launch {
            val isLoggedIn = authRepository.isUserLoggedIn()
            val hasPin = authRepository.hasPin()
            val userId = authRepository.getCurrentUserId()
            val pinUserId = authRepository.getPinUserId()
            _uiState.update { it.copy(isLoggedIn = isLoggedIn, hasPin = hasPin, userId = userId, pinUserId = pinUserId) }
        }
    }

    fun register(email: String, password: String, callback: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val result = authRepository.register(email, password)
            result.fold(
                onSuccess = { uid -> _uiState.update { it.copy(isLoggedIn = true, userId = uid) }; callback(true, null) },
                onFailure = { callback(false, it.message) }
            )
        }
    }

    fun login(email: String, password: String, callback: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val result = authRepository.login(email, password)
            result.fold(
                onSuccess = { uid -> _uiState.update { it.copy(isLoggedIn = true, userId = uid) }; callback(true, null) },
                onFailure = { callback(false, it.message) }
            )
        }
    }

    fun sendPasswordReset(email: String, callback: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val result = authRepository.sendPasswordResetEmail(email)
            result.fold(
                onSuccess = { callback(true, null) },
                onFailure = { callback(false, it.message) }
            )
        }
    }

    fun savePin(pin: String, callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUserId()
            if (userId != null) {
                val pinHash = AuthRepositoryImpl.hashPin(pin)
                authRepository.savePin(pinHash, userId)
                _uiState.update { it.copy(hasPin = true) }
                callback(true)
            } else callback(false)
        }
    }

    fun checkPin(pin: String, callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            val pinHash = AuthRepositoryImpl.hashPin(pin)
            callback(authRepository.checkPin(pinHash))
        }
    }

    fun logout(callback: () -> Unit) {
        viewModelScope.launch {
            authRepository.logout()
            authRepository.clearPin()
            _uiState.value = AuthUiState()
            callback()
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            authRepository.logout()
            authRepository.clearPin()
            _uiState.value = AuthUiState()
        }
    }
}

data class AuthUiState(
    val isLoggedIn: Boolean = false,
    val hasPin: Boolean = false,
    val userId: String? = null,
    val pinUserId: String? = null
)

// ========== MainViewModel ==========
@HiltViewModel
class MainViewModel @Inject constructor(
    private val settingsRepository: SettingsRepositoryImpl,
    private val walletRepository: WalletRepositoryImpl
) : ViewModel() {

    private val _tasks = MutableStateFlow(
        CoinType.values().flatMap { coin ->
            listOf(
                MiningTask(coin, MiningMode.POOL),
                MiningTask(coin, MiningMode.SOLO)
            )
        }.toMutableList()
    )
    val tasks: StateFlow<List<MiningTask>> = _tasks.asStateFlow()

    private val visibleIndices = listOf(0, 1, 3, 5, 7, 9, 11)
    val visibleTasks: List<MiningTask> get() = visibleIndices.map { _tasks.value[it] }

    fun getRealIndex(visiblePosition: Int): Int = visibleIndices[visiblePosition]

    private val _computingUsage = MutableStateFlow(50)
    val computingUsage: StateFlow<Int> = _computingUsage.asStateFlow()

    private val _displayPercentages = MutableStateFlow<List<Double>>(emptyList())
    val displayPercentages: StateFlow<List<Double>> = _displayPercentages.asStateFlow()

    private val _wallets = MutableStateFlow<List<Wallet>>(emptyList())
    val wallets: StateFlow<List<Wallet>> = _wallets.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.getComputingUsage().collect { usage -> _computingUsage.value = usage; recalcPercentages() }
        }
        viewModelScope.launch {
            settingsRepository.getTaskWeights().collect { weights ->
                if (weights.size == _tasks.value.size) {
                    _tasks.value = _tasks.value.mapIndexed { index, task -> task.copy(weight = weights[index]) }.toMutableList()
                    recalcPercentages()
                }
            }
        }
        viewModelScope.launch {
            walletRepository.getAllWallets().collect { wallets -> _wallets.value = wallets }
        }
    }

    fun updateTaskWeight(realIndex: Int, newWeight: Int) {
        if (realIndex !in _tasks.value.indices) return
        _tasks.value = _tasks.value.toMutableList().apply { this[realIndex] = this[realIndex].copy(weight = newWeight.coerceIn(0, 100)) }
        recalcPercentages()
        viewModelScope.launch { settingsRepository.saveTaskWeights(_tasks.value.map { it.weight }) }
    }

    fun setComputingUsage(percent: Int) {
        _computingUsage.value = percent.coerceIn(0, 100)
        viewModelScope.launch { settingsRepository.saveComputingUsage(_computingUsage.value) }
        recalcPercentages()
    }

    private fun recalcPercentages() {
        val totalWeight = _tasks.value.sumOf { it.weight }.toDouble()
        val usage = _computingUsage.value.toDouble()
        _displayPercentages.value = if (totalWeight > 0) _tasks.value.map { (it.weight / totalWeight) * usage } else List(_tasks.value.size) { 0.0 }
    }

    fun getVisiblePercentage(visiblePosition: Int): Double = _displayPercentages.value.getOrNull(getRealIndex(visiblePosition)) ?: 0.0
}

// ========== MiningViewModel ==========
@HiltViewModel
class MiningViewModel @Inject constructor(
    private val miningRepository: MiningRepositoryImpl,
    private val walletRepository: WalletRepositoryImpl,
    private val poolRepository: PoolRepositoryImpl
) : ViewModel() {

    private val _uiState = MutableStateFlow(MiningUiState())
    val uiState: StateFlow<MiningUiState> = _uiState.asStateFlow()

    init {
        combine(
            miningRepository.miningStats,
            miningRepository.isMiningActive,
            walletRepository.getAllWallets(),
            flow { emit(poolRepository.getPoolsForCoin(CoinType.MONERO)) } // упрощённо
        ) { stats, isActive, wallets, pools ->
            _uiState.update { current ->
                current.copy(
                    currentCoin = stats.coin,
                    hashrate = stats.hashrate,
                    acceptedShares = stats.acceptedShares,
                    rejectedShares = stats.rejectedShares,
                    estimatedEarnings = stats.estimatedEarnings,
                    isMining = isActive,
                    allWallets = wallets,
                    allPools = pools
                )
            }
        }.launchIn(viewModelScope)
    }

    fun loadPoolsForCoin(coin: CoinType) {
        viewModelScope.launch { _uiState.update { it.copy(availablePools = poolRepository.getPoolsForCoin(coin)) } }
    }

    fun loadWalletsForCoin(coin: CoinType) {
        viewModelScope.launch { _uiState.update { it.copy(walletsForCoin = walletRepository.getWalletsForCoin(coin)) } }
    }

    fun selectMode(mode: MiningMode) { _uiState.update { it.copy(selectedMode = mode) } }
    fun selectPool(pool: Pool) { _uiState.update { it.copy(selectedPool = pool) } }
    fun selectWallet(wallet: Wallet) { _uiState.update { it.copy(selectedWallet = wallet) } }
    fun toggleWalletDropdown() { _uiState.update { it.copy(walletDropdownExpanded = !it.walletDropdownExpanded) } }
    fun togglePoolDropdown() { _uiState.update { it.copy(poolDropdownExpanded = !it.poolDropdownExpanded) } }

    fun startMining(coin: CoinType, mode: MiningMode, pool: Pool?, wallet: Wallet) {
        viewModelScope.launch { miningRepository.startMining(coin, mode, pool, wallet) }
    }

    fun stopMining() { viewModelScope.launch { miningRepository.stopMining() } }
}

data class MiningUiState(
    val selectedMode: MiningMode = MiningMode.POOL,
    val selectedPool: Pool? = null,
    val selectedWallet: Wallet? = null,
    val walletDropdownExpanded: Boolean = false,
    val poolDropdownExpanded: Boolean = false,
    val currentCoin: CoinType = CoinType.MONERO,
    val hashrate: Double = 0.0,
    val acceptedShares: Long = 0,
    val rejectedShares: Long = 0,
    val estimatedEarnings: Double = 0.0,
    val isMining: Boolean = false,
    val availablePools: List<Pool> = emptyList(),
    val walletsForCoin: List<Wallet> = emptyList(),
    val allWallets: List<Wallet> = emptyList(),
    val allPools: List<Pool> = emptyList()
)

// ========== WalletViewModel ==========
@HiltViewModel
class WalletViewModel @Inject constructor(
    private val walletRepository: WalletRepositoryImpl,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(WalletUiState())
    val uiState: StateFlow<WalletUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            walletRepository.getAllWallets().collect { wallets -> _uiState.value = _uiState.value.copy(wallets = wallets) }
        }
    }

    fun addWallet(address: String, coin: CoinType, label: String?, seedPhrase: String?) {
        viewModelScope.launch { walletRepository.addWallet(address, coin, label, seedPhrase) }
    }

    fun updateWallet(wallet: Wallet) {
        viewModelScope.launch { walletRepository.updateWallet(wallet) }
    }

    fun deleteWallet(id: Long) {
        viewModelScope.launch { walletRepository.deleteWallet(id) }
    }

    fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("wallet_data", text))
    }
}

data class WalletUiState(val wallets: List<Wallet> = emptyList())

// ========== CryptoPaymentViewModel ==========
@HiltViewModel
class CryptoPaymentViewModel @Inject constructor(
    private val paymentManager: CryptoPaymentManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(CryptoPaymentUiState())
    val uiState: StateFlow<CryptoPaymentUiState> = _uiState.asStateFlow()

    fun createInvoice(userId: String, currency: CryptoPaymentManager.CryptoCurrency) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = paymentManager.createInvoice(userId, currency)
            result.onSuccess { invoice ->
                _uiState.update { it.copy(isLoading = false, invoiceData = invoice, selectedCurrency = currency) }
                paymentManager.openPaymentPage(invoice.invoiceUrl)
            }.onFailure { error ->
                _uiState.update { it.copy(isLoading = false, error = error.message) }
            }
        }
    }

    fun resetState() { _uiState.value = CryptoPaymentUiState() }
}

data class CryptoPaymentUiState(
    val isLoading: Boolean = false,
    val selectedCurrency: CryptoPaymentManager.CryptoCurrency? = null,
    val invoiceData: CryptoPaymentManager.InvoiceData? = null,
    val error: String? = null
)
