@file:OptIn(ExperimentalMaterial3Api::class)

package com.epos.waiter.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.epos.waiter.AppViewModel
import com.epos.waiter.Screen
import com.epos.waiter.data.TableInfo
import com.epos.waiter.data.TableStatus
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.util.Locale

// ------------------------------------------------------------------ table look (template "Tables screen")

private data class TableLook(val top: Color, val edge: Color, val chair: Color, val ink: Color, val text: Color)

private fun TableStatus.look(): TableLook = when (this) {
    TableStatus.Free -> TableLook(Color(0xFFE9F6EE), Color(0xFF2E9D50), Color(0xFFA9D9B8), Color(0xFF1D6B36), Color(0xFF1D6B36))
    TableStatus.Ordered -> TableLook(Color(0xFF1E57B0), Color(0xFF1E57B0), Color(0xFF9DBBE8), Color.White, Color(0xFF1E57B0))
    TableStatus.Billed -> TableLook(Color(0xFFB3302A), Color(0xFFB3302A), Color(0xFFE6A6A2), Color.White, Color(0xFFB3302A))
}

private val amountFormat: NumberFormat = NumberFormat.getNumberInstance(Locale("en", "IN")).apply { maximumFractionDigits = 0 }

/** "Free", "4 pax · last KOT 12 min", "Bill 1,240" - only what the server sent. */
private fun TableInfo.statusLine(short: Boolean = false): String = when (status) {
    TableStatus.Free -> "Free"
    TableStatus.Billed -> amount?.let { "Bill " + amountFormat.format(it) } ?: "Billed"
    TableStatus.Ordered -> listOfNotNull(
        covers?.toInt()?.takeIf { it > 0 }?.let { "$it pax" },
        minutes?.toInt()?.let { if (short) "$it min" else "last KOT $it min" },
    ).joinToString(" · ").ifEmpty { "Ordered" }
}

private val TableInfo.isLocked: Boolean get() = !lockedBy.isNullOrBlank()

/** A square table seen from above with 4 chairs; the table top shows the number (and status on big tables). */
@Composable
private fun TableDrawing(t: TableInfo, size: Dp, showLine: Boolean) {
    val look = t.status.look()
    val chairLong = size * 0.30f
    val chairThick = size * 0.09f
    val inset = size * 0.14f
    val top = size - inset * 2
    Box(Modifier.size(size)) {
        val mid = (size - chairLong) / 2
        Box(Modifier.offset(mid, 0.dp).size(chairLong, chairThick).clip(RoundedCornerShape(50)).background(look.chair))
        Box(Modifier.offset(mid, size - chairThick).size(chairLong, chairThick).clip(RoundedCornerShape(50)).background(look.chair))
        Box(Modifier.offset(0.dp, mid).size(chairThick, chairLong).clip(RoundedCornerShape(50)).background(look.chair))
        Box(Modifier.offset(size - chairThick, mid).size(chairThick, chairLong).clip(RoundedCornerShape(50)).background(look.chair))
        Column(
            Modifier.offset(inset, inset).size(top).clip(RoundedCornerShape(size * 0.13f)).background(look.top)
                .border(2.dp, look.edge, RoundedCornerShape(size * 0.13f)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(t.number, color = look.ink, fontWeight = FontWeight.ExtraBold, fontSize = (size.value * 0.24f).sp, maxLines = 1)
            if (showLine) {
                Text(t.statusLine(short = true), color = look.ink, fontWeight = FontWeight.SemiBold,
                    fontSize = (size.value * 0.095f).sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 4.dp))
            }
        }
    }
}

@Composable
private fun LockBadge(modifier: Modifier = Modifier) {
    Box(modifier.size(24.dp).clip(CircleShape).background(Saffron), contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.Lock, contentDescription = "Open on another device", tint = Navy, modifier = Modifier.size(14.dp))
    }
}

/** Grid card: drawing + status line (phones, and tablets in grid view). */
@Composable
private fun TableCard(t: TableInfo, enabled: Boolean, onClick: () -> Unit) {
    val look = t.status.look()
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White)
            .border(if (t.isLocked) BorderStroke(2.dp, Saffron) else BorderStroke(1.dp, Color(0xFFE1E5EE)), RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(top = 8.dp, bottom = 8.dp),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TableDrawing(t, 84.dp, showLine = false)
            Text(t.statusLine(short = true), color = look.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (t.isLocked) LockBadge(Modifier.align(Alignment.TopEnd).padding(6.dp))
    }
}

@Composable
private fun CountTile(count: Int, label: String, color: Color, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(Color.White).border(1.dp, Color(0xFFE1E5EE), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text("$count", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = color)
        Text(label, fontSize = 12.sp, color = Color(0xFF4B5468))
    }
}

@Composable
private fun Counts(tables: List<TableInfo>, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CountTile(tables.count { it.status == TableStatus.Free }, "Free", TableStatus.Free.look().text, Modifier.weight(1f))
        CountTile(tables.count { it.status == TableStatus.Ordered }, "Ordered", TableStatus.Ordered.look().text, Modifier.weight(1f))
        CountTile(tables.count { it.status == TableStatus.Billed }, "Billed", TableStatus.Billed.look().text, Modifier.weight(1f))
    }
}

/** Tables placed where they are on the PC dashboard (M_Table X_POS / Y_POS), scaled to fit. */
@Composable
private fun FloorPlan(tables: List<TableInfo>, enabled: Boolean, onOpen: (TableInfo) -> Unit, modifier: Modifier) {
    BoxWithConstraints(
        modifier.clip(RoundedCornerShape(20.dp)).background(Color.White).border(1.dp, Color(0xFFE1E5EE), RoundedCornerShape(20.dp)),
    ) {
        val tableSize = 120.dp
        val pad = 20.dp
        val xs = tables.map { it.xPos ?: 0.0 }
        val ys = tables.map { it.yPos ?: 0.0 }
        val minX = xs.minOrNull() ?: 0.0
        val minY = ys.minOrNull() ?: 0.0
        val spanX = ((xs.maxOrNull() ?: 0.0) - minX).coerceAtLeast(1.0)
        val spanY = ((ys.maxOrNull() ?: 0.0) - minY).coerceAtLeast(1.0)
        val roomX = (maxWidth - tableSize - pad * 2).value.coerceAtLeast(1f)
        val roomY = (maxHeight - tableSize - pad * 2).value.coerceAtLeast(1f)
        val scale = minOf(roomX / spanX.toFloat(), roomY / spanY.toFloat(), 2.5f)
        for (t in tables) {
            val x = pad + (((t.xPos ?: 0.0) - minX).toFloat() * scale).dp
            val y = pad + (((t.yPos ?: 0.0) - minY).toFloat() * scale).dp
            Box(Modifier.offset(x, y).clip(RoundedCornerShape(18.dp)).clickable(enabled = enabled) { onOpen(t) }) {
                TableDrawing(t, tableSize, showLine = true)
                if (t.isLocked) LockBadge(Modifier.align(Alignment.TopEnd))
            }
        }
    }
}

/** Tablet side panel: open orders with covers, last KOT and amount. */
@Composable
private fun RunningPanel(tables: List<TableInfo>, enabled: Boolean, onOpen: (TableInfo) -> Unit, modifier: Modifier) {
    val running = tables.filter { it.status != TableStatus.Free }
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(Color.White).border(1.dp, Color(0xFFE1E5EE), RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Text("Running tables", fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(bottom = 6.dp))
        if (running.isEmpty()) Text("No open orders", color = Color(0xFF4B5468), fontSize = 13.sp)
        LazyColumn {
            items(running, key = { it.number }) { t ->
                val look = t.status.look()
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(enabled = enabled) { onOpen(t) }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(look.top), contentAlignment = Alignment.Center) {
                        Text(t.number, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Table ${t.number}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(t.statusLine(), fontSize = 12.sp, color = Color(0xFF4B5468), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    t.amount?.takeIf { it > 0 }?.let {
                        Text(amountFormat.format(it), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = look.text)
                    }
                    if (t.isLocked) LockBadge()
                }
                HorizontalDivider(color = Color(0xFFEEF0F5))
            }
        }
    }
}

// ------------------------------------------------------------------ screen

@Composable
fun TablesScreen(vm: AppViewModel) {
    var menuOpen by remember { mutableStateOf(false) }
    var showChange by remember { mutableStateOf(false) }
    var showJoin by remember { mutableStateOf(false) }
    var gridOnTablet by rememberSaveable { mutableStateOf(false) }
    val wide = isWide()
    val tables = vm.tables
    val hasPlan = tables.size > 1 && tables.any { (it.xPos ?: 0.0) != 0.0 || (it.yPos ?: 0.0) != 0.0 }
    val showFloor = wide && hasPlan && !gridOnTablet

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

    Column(Modifier.fillMaxSize().background(Color(0xFFF4F5F8))) {
        TopAppBar(
            title = {
                Column {
                    Text(vm.currentRoom?.label ?: "Tables", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(vm.user?.name.orEmpty(), style = MaterialTheme.typography.labelMedium)
                }
            },
            actions = {
                if (wide && hasPlan) {
                    OutlinedButton(
                        onClick = { gridOnTablet = !gridOnTablet },
                        border = BorderStroke(1.dp, Color(0xFF3A4766)),
                    ) { Text(if (gridOnTablet) "Floor plan" else "Grid view", color = Color.White) }
                }
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
                Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                items(vm.rooms, key = { it.id }) { r ->
                    FilterChip(
                        selected = vm.currentRoom?.id == r.id, onClick = { vm.selectRoom(r) }, label = { Text(r.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Navy, selectedLabelColor = Color.White, containerColor = Color.White,
                        ),
                    )
                }
            }
        }

        if (showFloor) {
            Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                FloorPlan(tables, !vm.busy, { vm.openTable(it) }, Modifier.weight(1f).fillMaxHeight())
                Column(Modifier.width(300.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Counts(tables)
                    RunningPanel(tables, !vm.busy, { vm.openTable(it) }, Modifier.weight(1f).fillMaxWidth())
                }
            }
        } else {
            Counts(tables, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(if (wide) 132.dp else 108.dp),
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                contentPadding = PaddingValues(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(tables, key = { it.number }) { t ->
                    TableCard(t, enabled = !vm.busy) { vm.openTable(t) }
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
