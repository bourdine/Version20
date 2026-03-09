package com.lottttto.miner

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.lottttto.miner.navigation.AppNavGraph
import com.lottttto.miner.navigation.AuthNavGraph
import com.lottttto.miner.viewmodels.AuthViewModel
import com.lottttto.miner.ui.theme.LotttttoTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Обработка deep link
        intent?.data?.let { uri ->
            if (uri.scheme == "lottttto" && uri.host == "payment" && uri.path == "/success") {
                // Можно обновить статус подписки через ViewModel, но проще через репозиторий
                // Пока просто сохраним флаг, но здесь не будем реализовывать
            }
        }

        setContent {
            LotttttoTheme {
                val authViewModel: AuthViewModel = hiltViewModel()
                val authUiState by authViewModel.uiState.collectAsStateWithLifecycle()

                if (authUiState.isLoggedIn && authUiState.hasPin) {
                    AppNavGraph()
                } else {
                    AuthNavGraph(
                        onAuthSuccess = { /* handled by state */ }
                    )
                }
            }
        }
    }
}
