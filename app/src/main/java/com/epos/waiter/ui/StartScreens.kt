@file:OptIn(ExperimentalMaterial3Api::class)

package com.epos.waiter.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.epos.waiter.AppViewModel
import com.epos.waiter.R
import com.epos.waiter.data.User

val NoInsets = WindowInsets(0, 0, 0, 0)

/** The ePOS logo (navy ticket-fork + wordmark) for light backgrounds. */
@Composable
fun EposLogo(height: androidx.compose.ui.unit.Dp = 48.dp) {
    Image(
        painter = painterResource(R.drawable.epos_logo_horizontal),
        contentDescription = "ePOS",
        modifier = Modifier.height(height),
    )
}

@Composable
fun brandBar() = TopAppBarDefaults.topAppBarColors(
    containerColor = MaterialTheme.colorScheme.primary,
    titleContentColor = MaterialTheme.colorScheme.onPrimary,
    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
)

/** Finds the ePOS computer on the Wi-Fi; typing the address stays available as a backup. */
@Composable
fun ConnectScreen(vm: AppViewModel) {
    var address by remember { mutableStateOf(vm.serverAddress) }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("ePOS T2K") }, colors = brandBar(), windowInsets = NoInsets)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 520.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                EposLogo()
                Text("Connect to the ePOS computer", style = MaterialTheme.typography.headlineSmall)
                Text("The phone must be on the restaurant Wi-Fi and ePOS Mobile Server must be running on the computer.",
                    style = MaterialTheme.typography.bodyMedium)

                if (vm.scanning) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 3.dp)
                        Spacer(Modifier.size(12.dp))
                        Text("Searching the Wi-Fi...")
                    }
                }
                for (s in vm.foundServers) {
                    Card(
                        Modifier.fillMaxWidth().clickable(enabled = !vm.busy) { vm.connect(s.address) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(s.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text(s.address, style = MaterialTheme.typography.bodyMedium)
                            Text("Tap to connect", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                OutlinedButton(onClick = { vm.scan() }, enabled = !vm.scanning, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Search again")
                }

                Spacer(Modifier.height(8.dp))
                Text("Or type the address shown in the ePOS Mobile Server window", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = address, onValueChange = { address = it },
                    label = { Text("Address, e.g. 192.168.1.20") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { vm.connect(address) }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = { vm.connect(address) }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text("Connect")
                }
            }
        }
    }
}

/** Pick your name, enter your password (checked on the phone, as before). */
@Composable
fun LoginScreen(vm: AppViewModel) {
    var selected by remember { mutableStateOf<User?>(vm.users.firstOrNull { it.id == vm.lastUserId }) }
    var password by remember { mutableStateOf("") }
    val title = vm.concern?.concernName?.trim().orEmpty().ifEmpty { "ePOS T2K" }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            actions = {
                IconButton(onClick = { vm.forgetServer() }) { Icon(Icons.Filled.Settings, contentDescription = "Change ePOS computer") }
            },
            colors = brandBar(), windowInsets = NoInsets,
        )
        Row(Modifier.fillMaxWidth().padding(16.dp, 16.dp, 16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Who are you?", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            EposLogo(height = 32.dp)
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(112.dp),
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(vm.users, key = { it.id }) { u ->
                val isSel = selected?.id == u.id
                Card(
                    Modifier.clickable { selected = u; password = "" },
                    border = if (isSel) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    ),
                ) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Avatar(u.image, u.name)
                        Spacer(Modifier.height(6.dp))
                        Text(u.name, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val sel = selected
            Column(Modifier.widthIn(max = 520.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text(if (sel == null) "Select your name above" else "Password for ${sel.name}") },
                    enabled = sel != null, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { sel?.let { vm.login(it, password) } }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { sel?.let { vm.login(it, password) } },
                    enabled = sel != null && !vm.busy,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("Login") }
            }
        }
    }
}
