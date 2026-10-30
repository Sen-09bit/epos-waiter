package com.epos.waiter.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.epos.waiter.AppViewModel
import com.epos.waiter.Screen
import com.epos.waiter.data.TableInfo
import com.epos.waiter.data.TableStatus

/*
 * Change table and Join tables, as full screens (template "Menu, change table, join tables").
 */

private val ToolCream = Color(0xFFF6F4EF)
private val ToolMuted = Color(0xFF5B6478)
private val Picked = Color(0xFFFDF7EC)

private enum class MiniLook(val top: Color, val edge: Color, val chair: Color, val ink: Color) {
    Ordered(Color(0xFF1E57B0), Color(0xFF1E57B0), Color(0xFF9DBBE8), Color.White),
    Free(Color(0xFFE9F6EE), Color(0xFF2E9D50), Color(0xFFA9D9B8), Color(0xFF1D6B36)),
    Main(Navy, Navy, Color(0xFF8A93A8), Color.White),
}

@Composable
private fun ToolHeader(title: String, sub: String, onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Navy).padding(start = 6.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White) }
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Text(sub, color = Color(0xFFC9D1E3), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun StepTitle(n: Int, title: String, hint: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(Navy), contentAlignment = Alignment.Center) {
            Text("$n", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
        }
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Navy)
        Text(hint, fontSize = 13.sp, color = ToolMuted)
    }
}

/** Small table drawing (56 dp) in a tappable tile with a saffron tick when picked. */
@Composable
private fun MiniTable(number: String, look: MiniLook, picked: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Box(
        modifier.height(84.dp).clip(RoundedCornerShape(14.dp)).background(if (picked) Picked else Color.White)
            .border(if (picked) BorderStroke(2.dp, Saffron) else BorderStroke(1.dp, Color(0xFFE1E5EE)), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(56.dp)) {
            val chair = RoundedCornerShape(3.dp)
            Box(Modifier.offset(20.dp, 0.dp).size(16.dp, 6.dp).clip(chair).background(look.chair))
            Box(Modifier.offset(20.dp, 50.dp).size(16.dp, 6.dp).clip(chair).background(look.chair))
            Box(Modifier.offset(0.dp, 20.dp).size(6.dp, 16.dp).clip(chair).background(look.chair))
            Box(Modifier.offset(50.dp, 20.dp).size(6.dp, 16.dp).clip(chair).background(look.chair))
            Box(
                Modifier.offset(9.dp, 9.dp).size(38.dp).clip(RoundedCornerShape(9.dp)).background(look.top)
                    .border(2.dp, look.edge, RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(number, color = look.ink, fontSize = if (number.length > 3) 12.sp else 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            }
        }
        if (picked) {
            Box(Modifier.align(Alignment.TopEnd).padding(5.dp).size(20.dp).clip(CircleShape).background(Saffron),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, contentDescription = "Selected", tint = Navy, modifier = Modifier.size(13.dp))
            }
        }
    }
}

/** Tiles four to a row (five or more on wide screens). */
@Composable
private fun TileGrid(tables: List<TableInfo>, empty: String, tile: @Composable (TableInfo, Modifier) -> Unit) {
    if (tables.isEmpty()) {
        Text(empty, fontSize = 13.sp, color = ToolMuted, modifier = Modifier.padding(vertical = 8.dp))
        return
    }
    val perRow = if (isWide()) 8 else 4
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in tables.chunked(perRow)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (t in row) tile(t, Modifier.weight(1f))
                repeat(perRow - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun NumberChip(number: String, look: MiniLook, picked: Boolean = false) {
    Box(
        Modifier.heightIn(min = 44.dp).widthIn(min = 44.dp).clip(RoundedCornerShape(12.dp))
            .background(if (picked) Picked else look.top)
            .border(2.dp, if (picked) Saffron else look.edge, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) { Text(number, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = if (picked) Navy else look.ink) }
}

@Composable
private fun ConfirmCard(text: String, button: String, enabled: Boolean, onClick: () -> Unit, summary: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 16.dp).clip(RoundedCornerShape(20.dp))
            .background(Color.White).border(1.dp, Color(0xFFECE8DE), RoundedCornerShape(20.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        summary()
        Text(text, fontSize = 13.sp, color = ToolMuted, textAlign = TextAlign.Center)
        Box(
            Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(16.dp))
                .background(if (enabled) Saffron else Saffron.copy(alpha = 0.4f))
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Text(button, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Navy) }
    }
}

@Composable
fun ChangeTableScreen(vm: AppViewModel) {
    var from by remember { mutableStateOf<String?>(null) }
    var to by remember { mutableStateOf<String?>(null) }
    val ordered = vm.tableChoices.filter { it.status == TableStatus.Ordered }
    val free = vm.tableChoices.filter { it.status == TableStatus.Free }
    Column(Modifier.fillMaxSize().background(ToolCream)) {
        ToolHeader("Change table", "${vm.currentRoom?.label.orEmpty()} · move an order to a free table") { vm.go(Screen.Tables) }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StepTitle(1, "Move from", "tables with an order")
            TileGrid(ordered, "No table has an open order.") { t, m ->
                MiniTable(t.number, MiniLook.Ordered, from == t.number, { from = t.number }, m)
            }
            StepTitle(2, "Move to", "free tables")
            TileGrid(free, "No free table in this room.") { t, m ->
                MiniTable(t.number, MiniLook.Free, to == t.number, { to = t.number }, m)
            }
        }
        val f = from
        val d = to
        ConfirmCard(
            text = if (f != null && d != null) "Order, covers and bill move to table $d. The kitchen gets a change-of-table slip."
            else "Pick the table to move and the free table to move it to.",
            button = if (f != null && d != null) "Move table $f to table $d" else "Move table",
            enabled = f != null && d != null && !vm.busy,
            onClick = { if (f != null && d != null) vm.changeTable(f, d) {} },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                NumberChip(f ?: "?", MiniLook.Ordered)
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "to", tint = Saffron, modifier = Modifier.size(28.dp))
                NumberChip(d ?: "?", MiniLook.Free)
            }
        }
    }
}

@Composable
fun JoinTablesScreen(vm: AppViewModel) {
    var main by remember { mutableStateOf<String?>(null) }
    val others = remember { mutableStateListOf<String>() }
    val free = vm.tableChoices.filter { it.status == TableStatus.Free }
    Column(Modifier.fillMaxSize().background(ToolCream)) {
        ToolHeader("Join tables", "${vm.currentRoom?.label.orEmpty()} · seat a big group across free tables") { vm.go(Screen.Tables) }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StepTitle(1, "Main table", "the order stays here")
            TileGrid(free, "No free table in this room.") { t, m ->
                MiniTable(t.number, if (main == t.number) MiniLook.Main else MiniLook.Free, main == t.number,
                    { main = t.number; others.remove(t.number) }, m)
            }
            StepTitle(2, "Join with", "tap one or more")
            TileGrid(free.filter { it.number != main }, "Pick the main table first.") { t, m ->
                val on = t.number in others
                MiniTable(t.number, MiniLook.Free, on, { if (on) others.remove(t.number) else others.add(t.number) }, m)
            }
        }
        val m = main
        val joined = others.toList()
        ConfirmCard(
            text = if (m != null && joined.isNotEmpty())
                "Table${if (joined.size > 1) "s" else ""} ${joined.joinToString(" and ")} join table $m and disappear from the plan until the bill is settled."
            else "Pick the main table, then the tables to join to it.",
            button = if (m != null && joined.isNotEmpty()) "Join ${joined.size + 1} tables" else "Join tables",
            enabled = m != null && joined.isNotEmpty() && !vm.busy,
            onClick = { if (m != null && joined.isNotEmpty()) vm.joinTables(m, joined) {} },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NumberChip(m ?: "?", MiniLook.Main)
                for (j in joined.take(4)) {
                    Text("+", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Saffron)
                    NumberChip(j, MiniLook.Free, picked = true)
                }
                if (joined.size > 4) Text("+${joined.size - 4} more", fontSize = 13.sp, color = ToolMuted)
            }
        }
    }
}
