package com.lottttto.miner.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lottttto.miner.BuildConfig
import com.lottttto.miner.models.*
import com.lottttto.miner.utils.CryptoPaymentManager
import com.lottttto.miner.viewmodels.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

// ==================== КОМПОНЕНТЫ ====================

@Composable
fun VerticalSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    maxValue: Int = 100,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 200.dp,
    width: androidx.compose.ui.unit.Dp = 40.dp,
    thumbColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    activeTrackColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    inactiveTrackColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
) {
    val density = LocalContext.current.resources.displayMetrics.density
    val sliderHeightPx = with(density) { height.value * density }
    val thumbRadiusPx = with(density) { 12f }
    val trackWidthPx = with(density) { 8f }

    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, _ ->
                        change.consume()
                        val y = change.position.y.coerceIn(0f, sliderHeightPx)
                        val newVal = ((sliderHeightPx - y) / sliderHeightPx * maxValue).roundToInt()
                        onValueChange(newVal.coerceIn(0, maxValue))
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val trackStart = Offset(width.toPx() / 2, 0f)
            val trackEnd = Offset(width.toPx() / 2, sliderHeightPx)
            drawLine(inactiveTrackColor, trackStart, trackEnd, trackWidthPx)

            val thumbY = sliderHeightPx * (1 - value / maxValue.toFloat())
            val activeTrackStart = Offset(width.toPx() / 2, sliderHeightPx)
            val activeTrackEnd = Offset(width.toPx() / 2, thumbY)
            drawLine(activeTrackColor, activeTrackStart, activeTrackEnd, trackWidthPx)

            drawCircle(thumbColor, thumbRadiusPx, center = Offset(width.toPx() / 2, thumbY))
        }
        Text(
            text = "$value%",
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
fun StackedWalletCarousel(
    wallets: List<Wallet>,
    onWalletClick: (Wallet) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        itemsIndexed(wallets) { index, wallet ->
            val layoutInfo = listState.layoutInfo
            val itemInfo = layoutInfo.visibleItemsInfo.find { it.index == index }
            val offset = itemInfo?.let {
                (it.offset + it.size / 2 - layoutInfo.viewportEndOffset / 2).toFloat()
            } ?: 0f

            val distance = (offset / layoutInfo.viewportEndOffset).coerceIn(-1f, 1f).absoluteValue
            val scale = 1f - distance * 0.2f
            val translationY = distance * 20f
            val rotationZ = -distance * 5f

            Card(
                modifier = Modifier
                    .width(200.dp)
                    .scale(scale)
                    .graphicsLayer {
                        this.translationY = translationY
                        this.rotationZ = rotationZ
                    }
                    .clickable { onWalletClick(wallet) },
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = wallet.coin.getDisplayName(),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Balance: 0.0000",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = wallet.address.take(12) + "...",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

// ==================== ЭКРАНЫ АУТЕНТИФИКАЦИИ ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermsScreen(onAgreed: () -> Unit) {
    var agreed by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "User Agreement",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Text(
                text = getTermsText(),
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = agreed,
                onCheckedChange = { agreed = it }
            )
            Text(
                text = "I have read and agree to the Terms of Use and Privacy Policy",
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        Button(
            onClick = onAgreed,
            enabled = agreed,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Continue")
        }
    }
}

private fun getTermsText(): String = """
    LOTTTTTO MINER USER AGREEMENT
    
    PLEASE READ THIS USER AGREEMENT CAREFULLY BEFORE USING THE APPLICATION.
    
    1. ACCEPTANCE OF TERMS
    By downloading, installing, or using the Lottttto Miner application ("App"), you agree to be bound by this User Agreement ("Agreement"). If you do not agree, do not use the App.
    
    2. DESCRIPTION OF SERVICE
    The App allows users to participate in cryptocurrency mining using their device's computing power. The App may include features such as wallet management, mining pool connections, and premium subscriptions.
    
    3. USER RESPONSIBILITIES
    You are solely responsible for:
    - The security of your wallet addresses and private keys.
    - Compliance with applicable laws regarding cryptocurrency mining.
    - Any consequences of mining, including device wear and electricity costs.
    
    4. FEES AND SUBSCRIPTIONS
    The App may offer premium subscriptions that reduce fees. All payments are processed through third-party payment providers.
    
    5. DISCLAIMER OF WARRANTIES
    THE APP IS PROVIDED "AS IS" WITHOUT WARRANTIES OF ANY KIND. WE DO NOT GUARANTEE THAT MINING WILL RESULT IN PROFITS OR THAT THE APP WILL BE UNINTERRUPTED.
    
    6. LIMITATION OF LIABILITY
    TO THE MAXIMUM EXTENT PERMITTED BY LAW, WE SHALL NOT BE LIABLE FOR ANY INDIRECT, INCIDENTAL, OR CONSEQUENTIAL DAMAGES ARISING FROM YOUR USE OF THE APP.
    
    7. CHANGES TO AGREEMENT
    We may update this Agreement from time to time. Continued use of the App constitutes acceptance of the new terms.
    
    8. GOVERNING LAW
    This Agreement shall be governed by the laws of [Your Country].
    
    Last updated: April 2025
""".trimIndent()

@Composable
fun RegisterScreen(
    onRegisterSuccess: () -> Unit,
    onNavigateToLogin: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Create Account",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 32.dp)
        )
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = { Text("Confirm Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        if (errorMessage != null) {
            Text(
                text = errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = {
                when {
                    email.isBlank() -> errorMessage = "Email cannot be empty"
                    password.isBlank() -> errorMessage = "Password cannot be empty"
                    password.length < 6 -> errorMessage = "Password must be at least 6 characters"
                    password != confirmPassword -> errorMessage = "Passwords do not match"
                    else -> {
                        isLoading = true
                        viewModel.register(email, password) { success, msg ->
                            isLoading = false
                            if (success) onRegisterSuccess()
                            else errorMessage = msg ?: "Registration failed"
                        }
                    }
                }
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp))
            else Text("Register")
        }
        TextButton(onClick = onNavigateToLogin) {
            Text("Already have an account? Sign in")
        }
    }
}

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToForgotPassword: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Welcome Back",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 32.dp)
        )
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        if (errorMessage != null) {
            Text(
                text = errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = {
                if (email.isBlank() || password.isBlank()) {
                    errorMessage = "Please fill all fields"
                    return@Button
                }
                isLoading = true
                viewModel.login(email, password) { success, msg ->
                    isLoading = false
                    if (success) onLoginSuccess()
                    else errorMessage = msg ?: "Login failed"
                }
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp))
            else Text("Login")
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onNavigateToForgotPassword) {
                Text("Forgot Password?")
            }
            TextButton(onClick = onNavigateToRegister) {
                Text("Create Account")
            }
        }
    }
}

@Composable
fun ForgotPasswordScreen(
    onBackToLogin: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    var email by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Reset Password",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 32.dp)
        )
        Text(
            text = "Enter your email address and we'll send you a link to reset your password.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        if (message != null) {
            Text(
                text = message!!,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = {
                if (email.isBlank()) {
                    message = "Please enter your email"
                    isError = true
                    return@Button
                }
                isLoading = true
                viewModel.sendPasswordReset(email) { success, errorMsg ->
                    isLoading = false
                    if (success) {
                        message = "Reset link sent! Check your email."
                        isError = false
                    } else {
                        message = errorMsg ?: "Failed to send reset email"
                        isError = true
                    }
                }
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp))
            else Text("Send Reset Link")
        }
        TextButton(onClick = onBackToLogin) {
            Text("Back to Login")
        }
    }
}

@Composable
fun SetPinScreen(
    onPinSet: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Set Quick Access PIN",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 32.dp)
        )
        Text(
            text = "Enter a 4-digit PIN for faster login next time",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 4) pin = it.filter { c -> c.isDigit() } },
            label = { Text("PIN (4 digits)") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword)
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = confirmPin,
            onValueChange = { if (it.length <= 4) confirmPin = it.filter { c -> c.isDigit() } },
            label = { Text("Confirm PIN") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword)
        )
        if (errorMessage != null) {
            Text(
                text = errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = {
                when {
                    pin.length != 4 -> errorMessage = "PIN must be exactly 4 digits"
                    pin != confirmPin -> errorMessage = "PINs do not match"
                    else -> {
                        viewModel.savePin(pin) { success ->
                            if (success) onPinSet()
                            else errorMessage = "Failed to save PIN"
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Continue")
        }
    }
}

@Composable
fun EnterPinScreen(
    email: String,
    onPinSuccess: () -> Unit,
    onForgotPin: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    var pin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Welcome back!",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = email,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 32.dp)
        )
        OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 4) pin = it.filter { c -> c.isDigit() } },
            label = { Text("Enter your 4-digit PIN") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword)
        )
        if (errorMessage != null) {
            Text(
                text = errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = {
                if (pin.length != 4) {
                    errorMessage = "PIN must be 4 digits"
                } else {
                    viewModel.checkPin(pin) { isValid ->
                        if (isValid) onPinSuccess()
                        else errorMessage = "Incorrect PIN"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Unlock")
        }
        TextButton(onClick = onForgotPin) {
            Text("Forgot PIN? Reset account")
        }
    }
}

// ==================== ГЛАВНЫЙ ЭКРАН ====================

@Composable
fun MainScreen(
    onNavigateToWallet: () -> Unit,
    onNavigateToPayment: () -> Unit,
    onNavigateToMining: (CoinType) -> Unit,
    onLogout: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    val computingUsage by viewModel.computingUsage.collectAsStateWithLifecycle()
    val visibleTasks = remember { viewModel.visibleTasks }
    val wallets by viewModel.wallets.collectAsStateWithLifecycle()
    var menuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Lottttto Miner", style = MaterialTheme.typography.headlineLarge)
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Wallets") },
                        onClick = {
                            menuExpanded = false
                            onNavigateToWallet()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Premium") },
                        onClick = {
                            menuExpanded = false
                            onNavigateToPayment()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Logout") },
                        onClick = {
                            menuExpanded = false
                            onLogout()
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (wallets.isNotEmpty()) {
            StackedWalletCarousel(
                wallets = wallets,
                onWalletClick = { onNavigateToWallet() }
            )
        } else {
            Text(
                text = "No wallets yet. Add one in Wallets section.",
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text("Computing Usage: $computingUsage%", style = MaterialTheme.typography.titleMedium)
        Slider(
            value = computingUsage.toFloat(),
            onValueChange = { viewModel.setComputingUsage(it.toInt()) },
            valueRange = 0f..100f,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text("Mining Tasks", style = MaterialTheme.typography.titleLarge)

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(visibleTasks.size) { position ->
                val realIndex = viewModel.getRealIndex(position)
                val task = visibleTasks[position]
                val percent = viewModel.getVisiblePercentage(position)
                val label = when (task.coin) {
                    CoinType.MONERO -> if (task.mode == MiningMode.POOL) "M(P)" else "M(S)"
                    CoinType.BITCOIN -> "BTC"
                    CoinType.LITECOIN -> "LTC"
                    CoinType.DOGECOIN -> "DOGE"
                    CoinType.BITCOIN_CASH -> "BCH"
                    CoinType.ZCASH -> "ZEC"
                    else -> task.coin.name.take(3)
                }
                Column(
                    modifier = Modifier.clickable { onNavigateToMining(task.coin) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(label, style = MaterialTheme.typography.labelLarge)
                    VerticalSlider(
                        value = task.weight,
                        onValueChange = { viewModel.updateTaskWeight(realIndex, it) },
                        modifier = Modifier.height(150.dp)
                    )
                    Text("%.1f%%".format(percent))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Max temp: 35°C/90°C", style = MaterialTheme.typography.bodyMedium)

        Spacer(modifier = Modifier.weight(1f))
    }
}

// ==================== ЭКРАН ДЕТАЛЕЙ МОНЕТЫ ====================

@Composable
fun MiningScreen(
    coinType: CoinType,
    onBack: () -> Unit,
    viewModel: MiningViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(coinType) {
        viewModel.loadWalletsForCoin(coinType)
        if (coinType == CoinType.MONERO) {
            viewModel.loadPoolsForCoin(coinType)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Заголовок
        Text(
            text = coinType.name,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Выбор кошелька
        ExposedDropdownMenuBox(
            expanded = uiState.walletDropdownExpanded,
            onExpandedChange = { viewModel.toggleWalletDropdown() }
        ) {
            TextField(
                value = uiState.selectedWallet?.let {
                    it.label ?: it.address.take(12) + "..."
                } ?: "Select Wallet",
                onValueChange = {},
                readOnly = true,
                label = { Text("Wallet") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = uiState.walletDropdownExpanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = uiState.walletDropdownExpanded,
                onDismissRequest = { viewModel.toggleWalletDropdown() }
            ) {
                uiState.walletsForCoin.forEach { wallet ->
                    DropdownMenuItem(
                        text = { Text(wallet.label ?: wallet.address, maxLines = 1) },
                        onClick = {
                            viewModel.selectWallet(wallet)
                            viewModel.toggleWalletDropdown()
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Для Monero: выбор режима и пула
        if (coinType == CoinType.MONERO) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                MiningMode.values().forEach { mode ->
                    FilterChip(
                        selected = uiState.selectedMode == mode,
                        onClick = { viewModel.selectMode(mode) },
                        label = { Text(mode.name) },
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (uiState.selectedMode == MiningMode.POOL) {
                ExposedDropdownMenuBox(
                    expanded = uiState.poolDropdownExpanded,
                    onExpandedChange = { viewModel.togglePoolDropdown() }
                ) {
                    TextField(
                        value = uiState.selectedPool?.name ?: "Select Pool",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Pool") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = uiState.poolDropdownExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = uiState.poolDropdownExpanded,
                        onDismissRequest = { viewModel.togglePoolDropdown() }
                    ) {
                        uiState.availablePools.forEach { pool ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(pool.name)
                                        Text(pool.url, style = MaterialTheme.typography.bodySmall)
                                    }
                                },
                                onClick = {
                                    viewModel.selectPool(pool)
                                    viewModel.togglePoolDropdown()
                                }
                            )
                        }
                    }
                }
            }
        } else {
            Text("Mode: Solo", style = MaterialTheme.typography.bodyLarge)
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Карточка со статистикой
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                StatRow("Hashrate", formatHashrate(uiState.hashrate))
                StatRow("Accepted", uiState.acceptedShares.toString())
                StatRow("Rejected", uiState.rejectedShares.toString())
                StatRow("Est. earnings", "%.8f ${coinType.name}".format(uiState.estimatedEarnings))
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Кнопка запуска/остановки
        Button(
            onClick = {
                if (uiState.isMining) {
                    viewModel.stopMining()
                } else {
                    if (uiState.selectedWallet == null) return@Button
                    val pool = if (coinType == CoinType.MONERO && uiState.selectedMode == MiningMode.POOL) uiState.selectedPool else null
                    viewModel.startMining(
                        coin = coinType,
                        mode = if (coinType == CoinType.MONERO) uiState.selectedMode else MiningMode.SOLO,
                        pool = pool,
                        wallet = uiState.selectedWallet!!
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = uiState.selectedWallet != null && (coinType != CoinType.MONERO || uiState.selectedMode != MiningMode.POOL || uiState.selectedPool != null),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (uiState.isMining) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary
            )
        ) {
            Text(if (uiState.isMining) "Stop" else "Start Mining")
        }
    }
}

private fun formatHashrate(hashrate: Double): String {
    return when {
        hashrate >= 1_000_000 -> "${DecimalFormat("#.##").format(hashrate / 1_000_000)} MH/s"
        hashrate >= 1_000 -> "${DecimalFormat("#.##").format(hashrate / 1_000)} KH/s"
        else -> "${DecimalFormat("#.##").format(hashrate)} H/s"
    }
}

// ==================== ЭКРАН КОШЕЛЬКОВ ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreen(
    onBack: () -> Unit,
    viewModel: WalletViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingWallet by remember { mutableStateOf<Wallet?>(null) }
    var selectedWalletForDetails by remember { mutableStateOf<Wallet?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Wallets") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(uiState.wallets) { wallet ->
                WalletCard(
                    wallet = wallet,
                    onEdit = { editingWallet = wallet },
                    onDelete = { viewModel.deleteWallet(wallet.id) },
                    onShowDetails = { selectedWalletForDetails = it }
                )
            }
        }
    }

    // Диалог добавления/редактирования
    if (showAddDialog || editingWallet != null) {
        WalletDialog(
            wallet = editingWallet,
            onDismiss = {
                showAddDialog = false
                editingWallet = null
            },
            onSave = { address, coin, label, seedPhrase ->
                if (editingWallet != null) {
                    viewModel.updateWallet(editingWallet!!.copy(address = address, coin = coin, label = label, seedPhrase = seedPhrase))
                } else {
                    viewModel.addWallet(address, coin, label, seedPhrase)
                }
                showAddDialog = false
                editingWallet = null
            }
        )
    }

    // Диалог деталей кошелька
    if (selectedWalletForDetails != null) {
        WalletDetailDialog(
            wallet = selectedWalletForDetails!!,
            onDismiss = { selectedWalletForDetails = null },
            onCopyAddress = { viewModel.copyToClipboard(it) },
            onCopySeed = { viewModel.copyToClipboard(it) }
        )
    }
}

@Composable
fun WalletCard(
    wallet: Wallet,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShowDetails: (Wallet) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onShowDetails(wallet) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = wallet.label ?: "Unnamed", style = MaterialTheme.typography.titleMedium)
                Text(text = wallet.address, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                Text(text = wallet.coin.name, style = MaterialTheme.typography.labelSmall)
            }
            Row {
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Edit") }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletDialog(
    wallet: Wallet?,
    onDismiss: () -> Unit,
    onSave: (String, CoinType, String?, String?) -> Unit
) {
    var address by remember { mutableStateOf(wallet?.address ?: "") }
    var selectedCoin by remember { mutableStateOf(wallet?.coin ?: CoinType.MONERO) }
    var label by remember { mutableStateOf(wallet?.label ?: "") }
    var seedPhrase by remember { mutableStateOf(wallet?.seedPhrase ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (wallet == null) "New Wallet" else "Edit Wallet") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Wallet Address") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label (optional)") },
                    singleLine = true
                )
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    TextField(
                        value = selectedCoin.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Coin") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        CoinType.values().forEach { coin ->
                            DropdownMenuItem(
                                text = { Text(coin.name) },
                                onClick = {
                                    selectedCoin = coin
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = seedPhrase,
                    onValueChange = { seedPhrase = it },
                    label = { Text("Seed phrase (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (address.isNotBlank()) {
                        onSave(address, selectedCoin, label.ifBlank { null }, seedPhrase.ifBlank { null })
                    }
                },
                enabled = address.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun WalletDetailDialog(
    wallet: Wallet,
    onDismiss: () -> Unit,
    onCopyAddress: (String) -> Unit,
    onCopySeed: (String) -> Unit
) {
    val context = LocalContext.current
    var showSeed by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(wallet.label ?: wallet.coin.getDisplayName()) },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Address", style = MaterialTheme.typography.labelLarge)
                            Text(wallet.address, style = MaterialTheme.typography.bodyMedium)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                IconButton(onClick = { onCopyAddress(wallet.address) }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy address")
                                }
                            }
                        }
                    }
                }

                if (wallet.seedPhrase != null) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Seed Phrase", style = MaterialTheme.typography.labelLarge)
                                if (showSeed) {
                                    Text(wallet.seedPhrase, style = MaterialTheme.typography.bodyMedium)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        IconButton(onClick = { onCopySeed(wallet.seedPhrase) }) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy seed")
                                        }
                                    }
                                } else {
                                    Button(
                                        onClick = { showSeed = true },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Show Seed Phrase")
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Button(
                        onClick = { exportWalletData(context, wallet) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary
                        )
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Download wallet data")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

private fun exportWalletData(context: Context, wallet: Wallet) {
    try {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "wallet_${wallet.coin.name}_$timestamp.txt"
        val file = File(context.getExternalFilesDir(null), fileName)

        FileWriter(file).use { writer ->
            writer.write("=== WALLET DATA ===\n")
            writer.write("Coin: ${wallet.coin.getDisplayName()}\n")
            writer.write("Label: ${wallet.label ?: "N/A"}\n")
            writer.write("Address: ${wallet.address}\n")
            if (wallet.seedPhrase != null) {
                writer.write("Seed Phrase: ${wallet.seedPhrase}\n")
            }
            writer.write("Export date: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}\n")
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(shareIntent, "Export wallet data"))

    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

// ==================== ЭКРАН ОПЛАТЫ ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CryptoPaymentScreen(
    onBack: () -> Unit,
    onPaymentSuccess: () -> Unit,
    viewModel: CryptoPaymentViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var selectedCurrency by remember { mutableStateOf<CryptoPaymentManager.CryptoCurrency?>(null) }
    var showCurrencySelector by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Premium Subscription",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = "Pay with cryptocurrency — no hidden fees",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Premium Benefits:", fontWeight = FontWeight.Bold)
                Text("• 0% commission on all mining rewards")
                Text("• Priority support")
                Text("• Access to all mining pools")
                Text("• 30-day detailed statistics")
                Text("• No advertisements")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Monthly price:", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "$1.99 USD",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showCurrencySelector = true }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Pay with:", style = MaterialTheme.typography.labelSmall)
                    Text(
                        text = selectedCurrency?.displayName ?: "Select cryptocurrency",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Icon(Icons.Default.ArrowDropDown, contentDescription = "Select")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (selectedCurrency == null) {
                    Toast.makeText(context, "Please select a currency", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                // Здесь нужно получить userId из AuthViewModel, но для простоты передадим тестовый
                val userId = "test_user_123"
                viewModel.createInvoice(userId, selectedCurrency!!)
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled = selectedCurrency != null && !uiState.isLoading
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                Text("Pay with ${selectedCurrency?.symbol ?: "crypto"}")
            }
        }

        if (uiState.invoiceData != null) {
            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Payment details:", fontWeight = FontWeight.Bold)
                    Text("Amount: ${uiState.invoiceData!!.amount} ${uiState.invoiceData!!.currency}")
                    Text("Status: ${if (uiState.invoiceData != null) "⏳ Awaiting payment" else ""}")
                }
            }
        }

        if (uiState.error != null) {
            Text(
                text = uiState.error!!,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        TextButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back")
        }
    }

    if (showCurrencySelector) {
        AlertDialog(
            onDismissRequest = { showCurrencySelector = false },
            title = { Text("Select Cryptocurrency") },
            text = {
                LazyColumn {
                    items(CryptoPaymentManager.CryptoCurrency.values().toList()) { currency ->
                        ListItem(
                            headlineContent = { Text(currency.displayName) },
                            supportingContent = { Text(currency.symbol) },
                            modifier = Modifier.clickable {
                                selectedCurrency = currency
                                showCurrencySelector = false
                            }
                        )
                        Divider()
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCurrencySelector = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
