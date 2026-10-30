package com.epos.waiter

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import com.epos.waiter.ui.ChangeTableScreen
import com.epos.waiter.ui.ConnectScreen
import com.epos.waiter.ui.JoinTablesScreen
import com.epos.waiter.ui.EposTheme
import com.epos.waiter.ui.ExpensesScreen
import com.epos.waiter.ui.LoginScreen
import com.epos.waiter.ui.OrderScreen
import com.epos.waiter.ui.PasswordScreen
import com.epos.waiter.ui.ProfileScreen
import com.epos.waiter.ui.TablesScreen

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // ePOS splash (navy, ticket-fork). Stays up while the app looks for the ePOS computer, at most 5 s.
        val splashStart = SystemClock.uptimeMillis()
        installSplashScreen().setKeepOnScreenCondition {
            vm.screen == Screen.Connect && (vm.busy || vm.scanning) &&
                SystemClock.uptimeMillis() - splashStart < 5_000
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EposTheme {
                val snackbar = remember { SnackbarHostState() }
                LaunchedEffect(Unit) {
                    vm.messages.collect { snackbar.showSnackbar(it) }
                }
                // The order screen has its own back handling (asks before discarding changes).
                BackHandler(enabled = vm.screen in setOf(Screen.Expenses, Screen.Profile, Screen.Password, Screen.ChangeTable, Screen.JoinTables)) {
                    vm.back()
                }
                Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { inner ->
                    Box(Modifier.fillMaxSize().padding(inner).imePadding()) {
                        when (vm.screen) {
                            Screen.Connect -> ConnectScreen(vm)
                            Screen.Login -> LoginScreen(vm)
                            Screen.Tables -> TablesScreen(vm)
                            Screen.Order -> OrderScreen(vm)
                            Screen.Expenses -> ExpensesScreen(vm)
                            Screen.Profile -> ProfileScreen(vm)
                            Screen.Password -> PasswordScreen(vm)
                            Screen.ChangeTable -> ChangeTableScreen(vm)
                            Screen.JoinTables -> JoinTablesScreen(vm)
                        }
                        if (vm.busy) {
                            LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
                        }
                    }
                }
            }
        }
    }
}
