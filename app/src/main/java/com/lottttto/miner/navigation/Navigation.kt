package com.lottttto.miner.navigation

import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lottttto.miner.models.CoinType
import com.lottttto.miner.ui.screens.*
import com.lottttto.miner.viewmodels.AuthViewModel

@Composable
fun AuthNavGraph(onAuthSuccess: () -> Unit) {
    val navController = rememberNavController()
    val viewModel: AuthViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.checkAuthState() }

    val startDestination = when {
        !uiState.isLoggedIn -> "landing"
        uiState.isLoggedIn && !uiState.hasPin -> "set_pin"
        uiState.isLoggedIn && uiState.hasPin -> "enter_pin"
        else -> "landing"
    }

    NavHost(navController, startDestination) {
        composable("landing") { LoginScreen( onLoginSuccess = {}, onNavigateToRegister = { navController.navigate("register") }, onNavigateToForgotPassword = { navController.navigate("forgot_password") }, viewModel = viewModel ) }
        composable("terms") { TermsScreen(onAgreed = { navController.navigate("register") { popUpTo("terms") { inclusive = true } } }) }
        composable("register") { RegisterScreen( onRegisterSuccess = {}, onNavigateToLogin = { navController.navigate("login") }, viewModel = viewModel ) }
        composable("login") { LoginScreen( onLoginSuccess = {}, onNavigateToRegister = { navController.navigate("register") }, onNavigateToForgotPassword = { navController.navigate("forgot_password") }, viewModel = viewModel ) }
        composable("forgot_password") { ForgotPasswordScreen( onBackToLogin = { navController.popBackStack() }, viewModel = viewModel ) }
        composable("set_pin") { SetPinScreen( onPinSet = { onAuthSuccess() }, viewModel = viewModel ) }
        composable("enter_pin") { EnterPinScreen( email = uiState.userId ?: "User", onPinSuccess = { onAuthSuccess() }, onForgotPin = { viewModel.clearAll(); navController.popBackStack("landing", inclusive = false) }, viewModel = viewModel ) }
    }

    LaunchedEffect(uiState.isLoggedIn, uiState.hasPin) {
        when {
            uiState.isLoggedIn && uiState.hasPin -> navController.navigate("enter_pin") { popUpTo("landing") { inclusive = true } }
            uiState.isLoggedIn && !uiState.hasPin -> navController.navigate("set_pin") { popUpTo("landing") { inclusive = true } }
            !uiState.isLoggedIn -> navController.navigate("landing") { popUpTo(0) { inclusive = true } }
        }
    }
}

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = hiltViewModel()

    NavHost(navController, "main") {
        composable("main") {
            MainScreen(
                onNavigateToWallet = { navController.navigate("wallet") },
                onNavigateToPayment = { navController.navigate("payment") },
                onNavigateToMining = { coinType -> navController.navigate("mining/${coinType.name}") },
                onLogout = { authViewModel.logout {} }
            )
        }
        composable("wallet") { WalletScreen(onBack = { navController.popBackStack() }) }
        composable("payment") { CryptoPaymentScreen( onBack = { navController.popBackStack() }, onPaymentSuccess = { navController.popBackStack("main", inclusive = false) } ) }
        composable(
            route = "mining/{coinType}",
            arguments = listOf(navArgument("coinType") { type = NavType.StringType })
        ) { backStackEntry ->
            val coinTypeName = backStackEntry.arguments?.getString("coinType") ?: CoinType.MONERO.name
            val coinType = try { CoinType.valueOf(coinTypeName) } catch (e: IllegalArgumentException) { CoinType.MONERO }
            MiningScreen(coinType = coinType, onBack = { navController.popBackStack() })
        }
    }
}
