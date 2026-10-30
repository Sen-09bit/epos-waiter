@file:OptIn(ExperimentalMaterial3Api::class)

package com.epos.waiter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import com.epos.waiter.AppViewModel
import com.epos.waiter.Screen
import com.epos.waiter.data.TableInfo
import com.epos.waiter.data.TableStatus

private fun TableStatus.color(): Color = when (this) {
    TableStatus.Free -> FreeGreen
    TableStatus.Ordered -> OrderedBlue
    TableStatus.Billed -> BilledRed
}

@Composable
fun TablesScreen(vm: AppViewModel) {
    var menuOpen by remember { mutableStateOf(false) }
    var showChange by remember { mutableStateOf(false) }
    var showJoin by remember { mutableStateOf(false) }
    val title = vm.currentRoom?.label ?: "Tables"

    // Refresh table colours every 15 s, only while this screen is visible and the app is open.
    val lifecycle = LocalLifecycleOwner.current
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(15_000)
                if (!vm.busy) vm.refreshTables(quiet = true)
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(vm.user?.name.orEmpty(), style = MaterialTheme.typography.labelMedium)
                }
            },
            actions = {
                IconButton(onClick = { vm.refreshTables() }) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh") }
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Menu") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Change table") }, onClick = { menuOpen = false; showChange = true })
                        DropdownMenuItem(text = { Text("Join tables") }, onClick = { menuOpen = false; showJoin = true })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("Expenses") }, onClick = { menuOpen = false; vm.go(Screen.Expenses) })
                        DropdownMenuItem(text = { Text("Restaurant profile") }, onClick = { menuOpen = false; vm.go(Screen.Profile) })
                        DropdownMenuItem(text = { Text("Change password") }, onClick = { menuOpen = false; vm.go(Screen.Password) })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("Reload menu & prices") }, onClick = { menuOpen = false; vm.reloadMenu() })
                        DropdownMenuItem(text = { Text("Change ePOS computer") }, onClick = { menuOpen = false; vm.forgetServer() })
                        DropdownMenuItem(text = { Text("Logout") }, onClick = { menuOpen = false; vm.logout() })
                    }
                }
            },
            colors = brandBar(), windowInsets = NoInsets,
        )

        if (vm.rooms.size > 1) {
            LazyRow(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
            ) {
                items(vm.rooms, key = { it.id }) { r ->
                    FilterChip(selected = vm.currentRoom?.id == r.id, onClick = { vm.selectRoom(r) }, label = { Text(r.label) })
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Legend(FreeGreen, "Free")
            Legend(OrderedBlue, "Ordered")
            Legend(BilledRed, "Billed")
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(92.dp),
            modifier = Modifier.fillMaxSize().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(vm.tables, key = { it.number }) { t ->
                Box(
                    Modifier.aspectRatio(1f).clip(RoundedCornerShape(14.dp)).background(t.status.color())
                        .clickable(enabled = !vm.busy) { vm.openTable(t) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(t.number, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    vm.coversRequest?.let { req -> CoversDialog(req.table.number, vm) }
    vm.tableBusy?.let { busy ->
        AlertDialog(
            onDismissRequest = { vm.dismissTableBusy() },
            confirmButton = { TextButton(onClick = { vm.dismissTableBusy() }) { Text("OK") } },
            title = { Text("Table ${busy.table} is in use") },
            text = { Column { busy.lines.forEach { Text(it) } } },
        )
    }
    if (showChange) ChangeTableDialog(vm) { showChange = false }
    if (showJoin) JoinTablesDialog(vm) { showJoin = false }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(color))
        Spacer(Modifier.size(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

/** Old app: "Enter Number of Cover" + "Customer Type: Regular / New". */
@Composable
private fun CoversDialog(table: String, vm: AppViewModel) {
    var covers by remember { mutableStateOf(0) }
    var type by remember { mutableStateOf("Regular") }
    AlertDialog(
        onDismissRequest = { vm.coversCancelled() },
        title = { Text("Table $table") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Number of covers", style = MaterialTheme.typography.titleSmall)
                LazyVerticalGrid(GridCells.Fixed(5), Modifier.heightIn(max = 220.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items((1..20).toList()) { n ->
                        val sel = covers == n
                        Box(
                            Modifier.aspectRatio(1f).clip(RoundedCornerShape(10.dp))
                                .background(if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer)
                                .clickable { covers = n },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("$n", color = if (sel) Color.White else MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Text("Customer type", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    for (t in listOf("Regular", "New")) {
                        RadioButton(selected = type == t, onClick = { type = t })
                        Text(t, Modifier.clickable { type = t }.padding(end = 16.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.coversChosen(covers, type) }, enabled = covers > 0) { Text("Start order") }
        },
        dismissButton = { TextButton(onClick = { vm.coversCancelled() }) { Text("Cancel") } },
    )
}

/** Move an open order (blue table) to a free table (green). */
@Composable
private fun ChangeTableDialog(vm: AppViewModel, close: () -> Unit) {
    LaunchedEffect(Unit) { vm.loadChangeTableChoices() }
    var source by remember { mutableStateOf<String?>(null) }
    var target by remember { mutableStateOf<String?>(null) }
    val from = vm.tableChoices.filter { it.status == TableStatus.Ordered }
    val to = vm.tableChoices.filter { it.status == TableStatus.Free }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("Change table") },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TablePickList("From (ordered)", from, source, Modifier.weight(1f)) { source = it }
                TablePickList("To (free)", to, target, Modifier.weight(1f)) { target = it }
            }
        },
        confirmButton = {
            TextButton(
                enabled = source != null && target != null && !vm.busy,
                onClick = { vm.changeTable(source!!, target!!, close) },
            ) { Text("Change") }
        },
        dismissButton = { TextButton(onClick = close) { Text("Close") } },
    )
}

@Composable
private fun TablePickList(title: String, list: List<TableInfo>, selected: String?, modifier: Modifier, pick: (String) -> Unit) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        if (list.isEmpty()) Text("None", style = MaterialTheme.typography.bodySmall)
        LazyColumn(Modifier.heightIn(max = 300.dp)) {
            items(list, key = { it.number }) { t ->
                Row(Modifier.fillMaxWidth().clickable { pick(t.number) }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selected == t.number, onClick = { pick(t.number) })
                    Text("Table ${t.number}")
                }
            }
        }
    }
}

/** Join free tables to one table (same as the old app's Join Table). */
@Composable
private fun JoinTablesDialog(vm: AppViewModel, close: () -> Unit) {
    LaunchedEffect(Unit) { vm.loadJoinTableChoices() }
    var source by remember { mutableStateOf<String?>(null) }
    val others = remember { mutableStateListOf<String>() }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("Join tables") },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TablePickList("Main table", vm.tableChoices, source, Modifier.weight(1f)) {
                    source = it; others.remove(it)
                }
                Column(Modifier.weight(1f)) {
                    Text("Join with", style = MaterialTheme.typography.titleSmall)
                    LazyColumn(Modifier.heightIn(max = 300.dp)) {
                        items(vm.tableChoices.filter { it.number != source }, key = { it.number }) { t ->
                            val checked = t.number in others
                            Row(
                                Modifier.fillMaxWidth().clickable { if (checked) others.remove(t.number) else others.add(t.number) },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = checked, onCheckedChange = { if (it) others.add(t.number) else others.remove(t.number) })
                                Text("Table ${t.number}")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = source != null && others.isNotEmpty() && !vm.busy,
                onClick = { vm.joinTables(source!!, others.toList(), close) },
            ) { Text("Join") }
        },
        dismissButton = { TextButton(onClick = close) { Text("Close") } },
    )
}
