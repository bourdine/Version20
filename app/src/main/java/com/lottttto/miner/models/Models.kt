package com.lottttto.miner.models

import java.util.Date

// ---------- CoinType ----------
enum class CoinType {
    MONERO, BITCOIN, BITCOIN_CASH, LITECOIN, DOGECOIN, ZCASH
}

fun CoinType.getDisplayName(): String = when (this) {
    CoinType.MONERO -> "Monero (XMR)"
    CoinType.BITCOIN -> "Bitcoin (BTC)"
    CoinType.BITCOIN_CASH -> "Bitcoin Cash (BCH)"
    CoinType.LITECOIN -> "Litecoin (LTC)"
    CoinType.DOGECOIN -> "Dogecoin (DOGE)"
    CoinType.ZCASH -> "ZCash (ZEC)"
}

// ---------- MiningMode ----------
enum class MiningMode { SOLO, POOL }

// ---------- MiningAlgorithm ----------
enum class MiningAlgorithm(val algoName: String) {
    RANDOM_X("rx/0"),
    SHA256D("sha256d"),
    SCRYPT("scrypt"),
    EQUIHASH("equihash")
}

fun CoinType.getAlgorithm(): MiningAlgorithm = when (this) {
    CoinType.MONERO -> MiningAlgorithm.RANDOM_X
    CoinType.BITCOIN, CoinType.BITCOIN_CASH -> MiningAlgorithm.SHA256D
    CoinType.LITECOIN, CoinType.DOGECOIN -> MiningAlgorithm.SCRYPT
    CoinType.ZCASH -> MiningAlgorithm.EQUIHASH
}

// ---------- Pool ----------
data class Pool(
    val coin: CoinType,
    val name: String,
    val url: String,
    val description: String
)

// ---------- Wallet ----------
data class Wallet(
    val id: Long = 0,
    val address: String,
    val coin: CoinType,
    val label: String? = null,
    val seedPhrase: String? = null
)

// ---------- MiningTask ----------
data class MiningTask(
    val coin: CoinType,
    val mode: MiningMode,
    var weight: Int = 0
)

// ---------- MiningStats ----------
data class MiningStats(
    val coin: CoinType,
    val hashrate: Double,
    val acceptedShares: Long,
    val rejectedShares: Long,
    val estimatedEarnings: Double
)

// ---------- User ----------
data class User(
    val firebaseUid: String,
    val email: String,
    val premiumUntil: Date? = null
)
