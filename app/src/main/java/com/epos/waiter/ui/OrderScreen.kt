@file:OptIn(ExperimentalMaterial3Api::class)

package com.epos.waiter.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.Canvas
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.epos.waiter.AppViewModel
import com.epos.waiter.data.CartLine
import com.epos.waiter.data.MenuItem
import com.epos.waiter.data.money

// ---------------------------------------------------------------- colours of the order screens (template)

private val Cream = Color(0xFFF6F4EF)
private val CardLine = Color(0xFFECE8DE)
private val Dashed = Color(0xFFE3DED2)
private val Muted = Color(0xFF5B6478)
private val NoteBg = Color(0xFFFDEBC8)
private val NoteInk = Color(0xFF7A4B00)
private val NewInk = Color(0xFFB45309)
private val SentInk = Color(0xFF1D6B36)
private val Danger = Color(0xFFB3302A)
private val SelectedBg = Color(0xFFFDF7EC)

/** Which pop-up is open on the order screen. */
private sealed interface OrderDialog {
    data class Combo(val item: MenuItem) : OrderDialog
    data class Note(val key: Long) : OrderDialog
    data class Position(val key: Long) : OrderDialog
    data class Price(val key: Long) : OrderDialog
    data class ConfirmDelete(val key: Long) : OrderDialog
    data class ConfirmCancel(val key: Long) : OrderDialog
    data object SectionDiscount : OrderDialog
    data object ConfirmLeave : OrderDialog
    data object ConfirmInvoice : OrderDialog
}

/** One dish on the ticket with its comment / seat lines and combo choices. */
private class TicketGroup(val main: CartLine, val notes: List<CartLine>, val subs: List<CartLine>) {
    val sent: Boolean get() = main.isSent
    val value: Double get() = main.value + notes.sumOf { it.value }
}

/** Groups the flat KOT lines the way the ticket shows them (comments follow their dish, sub items their combo). */
private fun groupLines(lines: List<CartLine>): List<TicketGroup> {
    val groups = mutableListOf<TicketGroup>()
    val notes = mutableMapOf<Long, MutableList<CartLine>>()
    val subs = mutableMapOf<Long, MutableList<CartLine>>()
    var lastMain: CartLine? = null
    for (l in lines) {
        when {
            l.isNote && lastMain != null && lastMain.itemCode == l.itemCode -> notes.getOrPut(lastMain.key) { mutableListOf() }.add(l)
            l.isSubItem -> {
                val parent = lines.firstOrNull { it.isComboMain && it.isCombo + 1000 == l.isCombo }
                if (parent != null) subs.getOrPut(parent.key) { mutableListOf() }.add(l)
                else groups.add(TicketGroup(l, emptyList(), emptyList()))
            }
            else -> {
                groups.add(TicketGroup(l, emptyList(), emptyList()))
                lastMain = l
            }
        }
    }
    return groups.map { TicketGroup(it.main, notes[it.main.key].orEmpty(), subs[it.main.key].orEmpty()) }
}

private fun amount(v: Double): String = if (v == Math.floor(v)) String.format(java.util.Locale.US, "%,.0f", v) else money(v)

// ---------------------------------------------------------------- screen

@Composable
fun OrderScreen(vm: AppViewModel) {
    var selectedKey by remember { mutableStateOf<Long?>(null) }
    var dialog by remember { mutableStateOf<OrderDialog?>(null) }
    var showTicket by rememberSaveable { mutableStateOf(false) } // phone: menu or "View order"
    var section by rememberSaveable { mutableStateOf(vm.sections.firstOrNull()?.name.orEmpty()) }
    var query by rememberSaveable { mutableStateOf("") }
    val wide = isWide()

    fun indexOf(key: Long?): Int = if (key == null) -1 else vm.lines.indexOfFirst { it.key == key }

    fun addItem(item: MenuItem) {
        if (vm.busy) return // saving: don't change the order underneath it
        if (vm.comboSections(item).isNotEmpty()) {
            dialog = OrderDialog.Combo(item)
        } else {
            val i = vm.addItem(item)
            selectedKey = vm.lines.getOrNull(i)?.key
            if (vm.askPositionAfterItem && selectedKey != null) dialog = OrderDialog.Position(selectedKey!!)
        }
    }

    /** "−" on a dish card: one less of the newest unsent line of that dish. */
    fun removeOne(item: MenuItem) {
        if (vm.busy) return
        val i = vm.lines.indexOfLast { it.itemCode == item.code && !it.isSent && !it.isNote && !it.isSubItem && !it.isCancelled && it.qty > 0 }
        if (i < 0) return
        val l = vm.lines[i]
        if (l.qty > 1 && l.isCombo == 0) vm.edit { changeQty(i, -1) } else vm.edit { delete(i) }
    }

    val leave: () -> Unit = {
        when {
            !wide && showTicket -> showTicket = false
            vm.changed -> dialog = OrderDialog.ConfirmLeave
            else -> vm.leaveOrder()
        }
    }
    BackHandler(enabled = dialog == null) { leave() }

    val newQty: (MenuItem) -> Int = { item ->
        vm.lines.filter { it.itemCode == item.code && !it.isSent && !it.isNote && !it.isSubItem && !it.isCancelled && it.qty > 0 }.sumOf { it.qty }
    }

    Column(Modifier.fillMaxSize().background(Cream)) {
        OrderHeader(vm, wide, showTicket && !wide, query, { query = it }, leave, onAddItems = { showTicket = false })

        if (wide) {
            Row(Modifier.fillMaxSize()) {
                SectionRail(vm, section, { section = it; query = "" }, Modifier.width(196.dp).fillMaxHeight())
                DishGrid(vm, section, query, newQty, ::addItem, ::removeOne, Modifier.weight(1f).fillMaxHeight())
                Ticket(vm, selectedKey, { selectedKey = it }, { dialog = it },
                    Modifier.width(372.dp).fillMaxHeight().padding(start = 8.dp, end = 16.dp, top = 16.dp, bottom = 16.dp))
            }
        } else if (showTicket) {
            Ticket(vm, selectedKey, { selectedKey = it }, { dialog = it }, Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 12.dp))
        } else {
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    if (query.isBlank()) SectionTiles(vm, section) { section = it }
                    DishList(vm, section, query, newQty, ::addItem, ::removeOne, Modifier.weight(1f))
                }
                OrderBar(vm, Modifier.align(Alignment.BottomCenter).padding(12.dp)) { showTicket = true }
            }
        }
    }

    OrderDialogs(vm, dialog, { dialog = it }, ::indexOf) { selectedKey = it }
}

// ---------------------------------------------------------------- header

@Composable
private fun OrderHeader(
    vm: AppViewModel, wide: Boolean, ticketPage: Boolean, query: String, onQuery: (String) -> Unit,
    onBack: () -> Unit, onAddItems: () -> Unit,
) {
    val sub = listOfNotNull(
        vm.currentRoom?.label,
        if (vm.covers > 0) "${vm.covers} pax" else null,
        vm.customerType.ifBlank { null },
        if (wide) vm.user?.name else null,
    ).joinToString(" · ")
    val hasSaved = vm.lines.any { it.saved }

    Column(Modifier.fillMaxWidth().background(Navy).padding(start = 6.dp, end = 12.dp, top = 8.dp, bottom = if (wide || ticketPage) 10.dp else 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = if (ticketPage) "Back to menu" else "Back to tables", tint = Color.White)
            }
            Column(if (wide) Modifier.width(260.dp) else Modifier.weight(1f)) {
                Text(if (ticketPage) "Table ${vm.orderTable} · Order" else "Table ${vm.orderTable}",
                    color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                if (sub.isNotEmpty()) Text(sub, color = Color(0xFFC9D1E3), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (wide) {
                SearchBox(query, onQuery, Modifier.weight(1f))
            }
            if (ticketPage) {
                Box(Modifier.clip(RoundedCornerShape(12.dp)).border(1.dp, Color(0xFF3A4766), RoundedCornerShape(12.dp))
                    .clickable(onClick = onAddItems).padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text("+ Add items", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            } else if (hasSaved) {
                Box(Modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xFF26355A)).padding(horizontal = 12.dp, vertical = 7.dp)) {
                    Text("Open order", color = Color(0xFFFFD58A), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (!wide && !ticketPage) {
            Spacer(Modifier.height(10.dp))
            SearchBox(query, onQuery, Modifier.fillMaxWidth().padding(start = 6.dp))
        }
    }
}

@Composable
private fun SearchBox(query: String, onQuery: (String) -> Unit, modifier: Modifier) {
    Row(
        modifier.height(46.dp).clip(RoundedCornerShape(14.dp)).background(Color.White).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = Muted, modifier = Modifier.size(20.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text("Search dishes", color = Muted, fontSize = 15.sp)
            BasicTextField(
                value = query, onValueChange = onQuery, singleLine = true,
                textStyle = TextStyle(fontFamily = Jakarta, fontSize = 15.sp, color = Navy),
                cursorBrush = SolidColor(Navy),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Icon(Icons.Filled.Clear, contentDescription = "Clear search", tint = Muted,
                modifier = Modifier.size(22.dp).clickable { onQuery("") })
        }
    }
}

// ---------------------------------------------------------------- sections

@Composable
private fun SectionTiles(vm: AppViewModel, selected: String, pick: (String) -> Unit) {
    LazyRow(
        Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        items(vm.sections, key = { it.name }) { s ->
            val on = s.name == selected
            val logo = rememberBase64Image(s.logo)
            Column(Modifier.width(76.dp).clickable { pick(s.name) }, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(58.dp).clip(RoundedCornerShape(18.dp)).background(if (on) Navy else Color.White)
                        .border(if (on) BorderStroke(2.dp, Saffron) else BorderStroke(1.dp, Color(0xFFE3DED2)), RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (logo != null) {
                        Image(logo, contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.size(if (on) 50.dp else 54.dp).clip(RoundedCornerShape(15.dp)))
                    } else {
                        Text(s.name.take(1).uppercase(), color = if (on) Saffron else Navy, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(s.name, fontSize = 12.sp, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold,
                    color = Navy, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun SectionRail(vm: AppViewModel, selected: String, pick: (String) -> Unit, modifier: Modifier) {
    LazyColumn(modifier.padding(start = 16.dp, end = 10.dp), contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(vm.sections, key = { it.name }) { s ->
            val on = s.name == selected
            Row(
                Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(14.dp)).background(if (on) Navy else Color.Transparent)
                    .clickable { pick(s.name) }.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(if (on) Saffron else Color.White),
                    contentAlignment = Alignment.Center) {
                    Text(s.name.take(1).uppercase(), color = Navy, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                }
                Text(s.name, Modifier.weight(1f), color = if (on) Color.White else Navy, fontSize = 14.sp,
                    fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val count = vm.itemsOf(s.name).size
                Text("$count", color = if (on) Color(0xFFC9D1E3) else Muted, fontSize = 12.sp)
            }
        }
    }
}

// ---------------------------------------------------------------- dishes

@Composable
private fun Stepper(qty: Int, height: Dp, onMinus: () -> Unit, onPlus: () -> Unit, filled: Boolean = true) {
    Row(
        Modifier.height(height).clip(RoundedCornerShape(height / 2))
            .background(if (filled) Navy else Color.White)
            .border(1.5.dp, Navy, RoundedCornerShape(height / 2)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val ink = if (filled) Color.White else Navy
        Box(Modifier.size(height).clickable(onClick = onMinus), contentAlignment = Alignment.Center) {
            Text("−", color = ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Text("$qty", color = ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
            modifier = Modifier.width(20.dp))
        Box(Modifier.size(height).clickable(onClick = onPlus), contentAlignment = Alignment.Center) {
            Text("+", color = ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ComboTag(text: String = "Combo") {
    Box(Modifier.clip(RoundedCornerShape(11.dp)).background(NoteBg).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(text, color = NoteInk, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

private fun MenuItem.subtitle(): String =
    listOfNotNull(courseType?.trim()?.ifEmpty { null }, department?.trim()?.ifEmpty { null }).joinToString(" · ")

/** Phone: list of dish cards with ADD / − n +. */
@Composable
private fun DishList(
    vm: AppViewModel, section: String, query: String, newQty: (MenuItem) -> Int,
    add: (MenuItem) -> Unit, remove: (MenuItem) -> Unit, modifier: Modifier,
) {
    val dishes = if (query.isBlank()) vm.itemsOf(section) else vm.search(query)
    LazyColumn(
        modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "heading") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.Bottom) {
                Text(if (query.isBlank()) section else "Results for \"$query\"", Modifier.weight(1f),
                    fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Navy, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${dishes.size} items", fontSize = 13.sp, color = Muted)
            }
        }
        items(dishes, key = { it.code }) { d ->
            val qty = newQty(d)
            val combo = vm.comboSections(d).isNotEmpty()
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White)
                    .border(1.dp, CardLine, RoundedCornerShape(16.dp)).padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(d.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Navy)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (d.priceValue > 0) Text(amount(d.priceValue), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Navy)
                        if (combo) ComboTag("Combo · pick ${vm.comboSections(d).size}")
                    }
                    val sub = d.subtitle()
                    if (sub.isNotEmpty()) Text(sub, fontSize = 12.sp, color = Muted)
                }
                if (qty > 0) {
                    Stepper(qty, 44.dp, { remove(d) }, { add(d) })
                } else {
                    Box(
                        Modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).background(Color.White)
                            .border(1.5.dp, Navy, RoundedCornerShape(22.dp)).clickable { add(d) }.padding(horizontal = 20.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("ADD", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Navy, letterSpacing = 0.5.sp) }
                }
            }
        }
    }
}

/** Tablet: dish cards in a grid. */
@Composable
private fun DishGrid(
    vm: AppViewModel, section: String, query: String, newQty: (MenuItem) -> Int,
    add: (MenuItem) -> Unit, remove: (MenuItem) -> Unit, modifier: Modifier,
) {
    val dishes = if (query.isBlank()) vm.itemsOf(section) else vm.search(query)
    Column(modifier.padding(start = 6.dp, end = 8.dp, top = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.Bottom) {
            Text(if (query.isBlank()) section else "Results for \"$query\"", Modifier.weight(1f),
                fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Navy, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${dishes.size} items · tap to add", fontSize = 13.sp, color = Muted)
        }
        Spacer(Modifier.height(12.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(190.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            items(dishes, key = { it.code }) { d ->
                val qty = newQty(d)
                val combo = vm.comboSections(d).isNotEmpty()
                Column(
                    Modifier.fillMaxWidth().heightIn(min = 128.dp).clip(RoundedCornerShape(18.dp)).background(Color.White)
                        .border(if (qty > 0) BorderStroke(2.dp, Navy) else BorderStroke(1.dp, CardLine), RoundedCornerShape(18.dp))
                        .clickable { add(d) }.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(d.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Navy, lineHeight = 19.sp)
                        val sub = d.subtitle()
                        if (sub.isNotEmpty()) Text(sub, fontSize = 12.sp, color = Muted)
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (d.priceValue > 0) Text(amount(d.priceValue), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Navy)
                            if (combo) ComboTag()
                        }
                        if (qty > 0) {
                            Stepper(qty, 40.dp, { remove(d) }, { add(d) })
                        } else {
                            Box(Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).border(1.5.dp, Navy, RoundedCornerShape(22.dp)),
                                contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Add, contentDescription = "Add ${d.name}", tint = Navy)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Phone: floating bar with the new items and a "View order" button. */
@Composable
private fun OrderBar(vm: AppViewModel, modifier: Modifier, onOpen: () -> Unit) {
    val groups = groupLines(vm.lines)
    val newGroups = groups.filter { !it.sent }
    val newCount = newGroups.filter { !it.main.isNote }.sumOf { maxOf(it.main.qty, 0) }
    val sentCount = groups.count { it.sent }
    Row(
        modifier.fillMaxWidth().height(64.dp).shadow(12.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp))
            .background(Navy).clickable(onClick = onOpen).padding(start = 18.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(if (newCount > 0) "$newCount new · ${amount(newGroups.sumOf { it.value })}" else "Total ${amount(vm.editor.totalValue)}",
                color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            Text(if (sentCount > 0) "$sentCount sent to kitchen earlier" else "Nothing sent yet", color = Color(0xFFC9D1E3), fontSize = 12.sp)
        }
        Row(
            Modifier.height(48.dp).clip(RoundedCornerShape(14.dp)).background(Saffron).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("View order", color = Navy, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Navy)
        }
    }
}

// ---------------------------------------------------------------- the ticket (phone "View order" and tablet right panel)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Ticket(vm: AppViewModel, selectedKey: Long?, select: (Long?) -> Unit, open: (OrderDialog) -> Unit, modifier: Modifier) {
    val groups = groupLines(vm.lines)
    val fresh = groups.filter { !it.sent }
    val sent = groups.filter { it.sent }
    val newLines = fresh.count { !it.main.isNote && it.main.qty > 0 }
    Column(modifier) {
        Column(
            Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)).background(Color.White)
                .padding(start = 16.dp, end = 16.dp, top = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("KITCHEN ORDER TICKET", Modifier.weight(1f), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp, color = Muted)
                Text("${groups.count { !it.main.isNote && !it.main.isCancelled }} items", fontSize = 12.sp, color = Muted)
            }
            DashedLine(Modifier.padding(vertical = 8.dp))
            if (vm.lines.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No items yet. Add dishes from the menu.", color = Muted, fontSize = 14.sp)
                }
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 10.dp)) {
                    if (fresh.isNotEmpty()) {
                        item(key = "h_new") { SectionLabel("NEW · NOT SENT YET", NewInk, check = false) }
                        items(fresh, key = { it.main.key }) { g -> TicketRow(vm, g, g.main.key == selectedKey, select, open) }
                    }
                    if (sent.isNotEmpty()) {
                        item(key = "d_sent") { DashedLine(Modifier.padding(top = 4.dp, bottom = 4.dp)) }
                        item(key = "h_sent") { SectionLabel("SENT TO KITCHEN", SentInk, check = true) }
                        items(sent, key = { it.main.key }) { g -> TicketRow(vm, g, g.main.key == selectedKey, select, open) }
                    }
                }
            }
            DashedLine(Modifier.padding(vertical = 6.dp))
            val sentValue = sent.sumOf { it.value }
            val newValue = fresh.sumOf { it.value }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Sent ${amount(sentValue)} · New ${amount(newValue)}", Modifier.weight(1f), fontSize = 13.sp, color = Muted)
                Text("% Section discount", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Navy,
                    modifier = Modifier.clickable(enabled = vm.lines.isNotEmpty()) { open(OrderDialog.SectionDiscount) }.padding(vertical = 6.dp))
            }
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(bottom = 10.dp)) {
                Text("Total", Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Navy)
                Text(amount(vm.editor.totalValue), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Navy)
            }
        }
        TornEdge(Modifier.fillMaxWidth().height(12.dp))
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BigButton("KOT", if (newLines > 0) "send $newLines new to kitchen" else "nothing new to send", Navy, Color.White,
                Color(0xFFC9D1E3), enabled = vm.lines.isNotEmpty() && !vm.busy, modifier = Modifier.weight(1f)) { vm.sendKot() }
            BigButton("INVOICE", "save & print bill", Saffron, Navy, Color(0xFF4A2F00),
                enabled = vm.lines.isNotEmpty() && !vm.busy, modifier = Modifier.weight(1f)) { open(OrderDialog.ConfirmInvoice) }
        }
    }
}

@Composable
private fun SectionLabel(text: String, color: Color, check: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(vertical = 4.dp)) {
        if (check) Icon(Icons.Filled.Check, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp, color = color)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TicketRow(vm: AppViewModel, g: TicketGroup, selected: Boolean, select: (Long?) -> Unit, open: (OrderDialog) -> Unit) {
    val l = g.main
    val cancelLine = l.isCancelled && l.qty < 0
    val ink = when {
        cancelLine -> Danger
        l.isCancelled -> Muted
        g.sent -> Muted
        else -> Navy
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (selected) SelectedBg else Color.Transparent)
            .border(1.5.dp, if (selected) Saffron else Color.Transparent, RoundedCornerShape(14.dp))
            .clickable { select(if (selected) null else l.key) }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (g.sent || l.isNote || l.isCancelled) {
                Text(if (l.isNote) "" else "${l.qty}", Modifier.width(26.dp), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ink)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    if (cancelLine) l.description + " · cancelled" else l.shownText,
                    fontSize = if (g.sent) 14.sp else 15.sp,
                    fontWeight = if (g.sent) FontWeight.Normal else FontWeight.Bold,
                    fontStyle = if (l.isNote) FontStyle.Italic else FontStyle.Normal,
                    color = ink,
                )
                if (!g.sent && !l.isNote && !l.isCancelled && l.price > 0) {
                    val was = if (l.aPrice > 0 && l.price != l.aPrice) "  (was ${amount(l.aPrice)})" else ""
                    Text("${amount(l.price)} each$was", fontSize = 12.sp, color = Muted)
                }
            }
            if (!g.sent && !l.isNote && !l.isCancelled && !l.isSubItem) {
                Stepper(l.qty, 36.dp,
                    onMinus = { val i = vm.lines.indexOfFirst { it.key == l.key }; if (l.qty > 1 && l.isCombo == 0) vm.edit { changeQty(i, -1) } else open(OrderDialog.ConfirmDelete(l.key)) },
                    onPlus = { val i = vm.lines.indexOfFirst { it.key == l.key }; if (l.isCombo == 0) vm.edit { changeQty(i, 1) } },
                    filled = false)
            }
            Text(amount(g.value), Modifier.width(60.dp), textAlign = TextAlign.End,
                fontSize = if (g.sent) 14.sp else 15.sp, fontWeight = if (g.sent) FontWeight.Normal else FontWeight.ExtraBold, color = ink)
        }
        if (g.subs.isNotEmpty() || g.notes.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(start = if (g.sent) 36.dp else 0.dp)) {
                for (s in g.subs) NoteChip(s.description.removePrefix("**"), removable = false) {}
                for (n in g.notes) NoteChip(n.shownText.replace("POS :", "Seat"), removable = !n.isSent && !vm.busy) {
                    val i = vm.lines.indexOfFirst { it.key == n.key }
                    if (i >= 0) vm.edit { delete(i) }
                }
            }
        }
        if (selected && !vm.busy) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val main = !l.isNote && !l.isSubItem && !l.isCancelled
                if (main) {
                    ActionChip("Comment") { open(OrderDialog.Note(l.key)) }
                    ActionChip("Seat") { open(OrderDialog.Position(l.key)) }
                    if (!g.sent && l.price > 0) ActionChip("Price") { open(OrderDialog.Price(l.key)) }
                }
                if (main && g.sent && l.isCombo == 0 && l.qty > 0) ActionChip("Cancel item", danger = true) { open(OrderDialog.ConfirmCancel(l.key)) }
                if (!l.isCancelled && (!g.sent || vm.editor.isAdmin)) ActionChip("Delete", danger = true) { open(OrderDialog.ConfirmDelete(l.key)) }
            }
        }
    }
}

@Composable
private fun NoteChip(text: String, removable: Boolean, onRemove: () -> Unit) {
    Row(
        Modifier.height(28.dp).clip(RoundedCornerShape(14.dp)).background(NoteBg).padding(start = 10.dp, end = if (removable) 4.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = NoteInk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        if (removable) {
            Box(Modifier.size(24.dp).clickable(onClick = onRemove), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Close, contentDescription = "Remove $text", tint = NoteInk, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun ActionChip(text: String, danger: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.height(38.dp).clip(RoundedCornerShape(10.dp)).background(Color.White)
            .border(1.dp, if (danger) Color(0xFFF1C2BF) else Dashed, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (danger) Danger else Navy) }
}

@Composable
private fun BigButton(title: String, sub: String, bg: Color, ink: Color, subInk: Color, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.height(60.dp).clip(RoundedCornerShape(16.dp)).background(if (enabled) bg else bg.copy(alpha = 0.45f))
            .clickable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, color = ink, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp)
        Text(sub, color = subInk, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun DashedLine(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(2.dp)) {
        var x = 0f
        val dash = 6.dp.toPx()
        while (x < size.width) {
            drawLine(Dashed, androidx.compose.ui.geometry.Offset(x, 1f), androidx.compose.ui.geometry.Offset(minOf(x + dash, size.width), 1f), strokeWidth = 2.dp.toPx())
            x += dash * 2
        }
    }
}

/** Zig-zag bottom edge of the paper ticket (the ePOS logo's torn edge). */
@Composable
private fun TornEdge(modifier: Modifier) {
    Canvas(modifier) {
        val tooth = size.height
        val path = Path().apply {
            moveTo(0f, 0f)
            var x = 0f
            while (x < size.width) {
                lineTo(minOf(x + tooth, size.width), size.height)
                lineTo(minOf(x + tooth * 2, size.width), 0f)
                x += tooth * 2
            }
            close()
        }
        drawPath(path, Color.White)
    }
}

// ---------------------------------------------------------------- dialogs

@Composable
private fun OrderDialogs(
    vm: AppViewModel, dialog: OrderDialog?, setDialog: (OrderDialog?) -> Unit,
    indexOf: (Long?) -> Int, select: (Long?) -> Unit,
) {
    when (val d = dialog) {
        null -> Unit
        is OrderDialog.Combo -> ComboDialog(vm, d.item, onDone = { picks ->
            val i = vm.addCombo(d.item, 1, picks)
            select(vm.lines.getOrNull(i)?.key)
            setDialog(null)
        }, onClose = { setDialog(null) })
        is OrderDialog.Note -> NoteDialog(vm, onPick = { text ->
            val i = indexOf(d.key)
            vm.edit { addNote(i, text) }
            setDialog(null)
        }, onClose = { setDialog(null) })
        is OrderDialog.Position -> PositionDialog(onPick = { seats ->
            val i = indexOf(d.key)
            vm.edit { addNote(i, "POS : $seats") }
            setDialog(null)
        }, onClose = { setDialog(null) })
        is OrderDialog.Price -> {
            val line = vm.lines.getOrNull(indexOf(d.key))
            if (line != null) PriceDialog(line, onSet = { p ->
                vm.edit { setPrice(indexOf(d.key), p) }
                setDialog(null)
            }, onClose = { setDialog(null) })
        }
        is OrderDialog.ConfirmDelete -> ConfirmDialog("Delete this item?", "It will be removed from the order.", "Delete",
            onYes = { vm.edit { delete(indexOf(d.key)) }; select(null); setDialog(null) }, onNo = { setDialog(null) })
        is OrderDialog.ConfirmCancel -> ConfirmDialog("Cancel this item?", "The kitchen will get a \"Cancel Item\" ticket with the next KOT.", "Cancel item",
            onYes = { vm.edit { cancel(indexOf(d.key)) }; setDialog(null) }, onNo = { setDialog(null) })
        OrderDialog.SectionDiscount -> SectionDiscountDialog(vm.editor.sectionsInOrder(), onApply = { sections, pct ->
            vm.edit { sectionDiscount(sections, pct) }
            setDialog(null)
        }, onClose = { setDialog(null) })
        OrderDialog.ConfirmLeave -> ConfirmDialog("Leave without sending?", "Changes not sent with KOT will be lost.", "Leave",
            onYes = { setDialog(null); vm.leaveOrder() }, onNo = { setDialog(null) })
        OrderDialog.ConfirmInvoice -> ConfirmDialog("Print the bill?", "The order is saved and the bill is printed.", "Invoice",
            onYes = { setDialog(null); vm.sendInvoice() }, onNo = { setDialog(null) })
    }
}

@Composable
private fun ConfirmDialog(title: String, text: String, yes: String, onYes: () -> Unit, onNo: () -> Unit) {
    AlertDialog(
        onDismissRequest = onNo,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onYes) { Text(yes) } },
        dismissButton = { TextButton(onClick = onNo) { Text("No") } },
    )
}

/** Combo: pick one item from every sub-section (frmSubItems). */
@Composable
private fun ComboDialog(vm: AppViewModel, item: MenuItem, onDone: (List<MenuItem>) -> Unit, onClose: () -> Unit) {
    val sections = remember(item) { vm.comboSections(item) }
    val picks = remember { mutableStateMapOf<String, MenuItem>() }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(item.name) },
        text = {
            LazyColumn(Modifier.heightIn(max = 460.dp)) {
                for (s in sections) {
                    item(key = "h_$s") {
                        Text(s, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                    }
                    items(vm.comboChoices(s), key = { "${s}_${it.code}" }) { c ->
                        Row(Modifier.fillMaxWidth().clickable { picks[s] = c }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = picks[s]?.code == c.code, onClick = { picks[s] = c })
                            Text(c.name, Modifier.weight(1f))
                            if (c.subPriceValue > 0) Text("+" + money(c.subPriceValue))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = sections.all { picks.containsKey(it) },
                onClick = { onDone(sections.mapNotNull { picks[it] }) },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Close") } },
    )
}

/** Saved comments (M_Comments) or a free comment. */
@Composable
private fun NoteDialog(vm: AppViewModel, onPick: (String) -> Unit, onClose: () -> Unit) {
    var free by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Comment") },
        text = {
            Column {
                OutlinedTextField(value = free, onValueChange = { free = it }, label = { Text("Type a comment") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(vm.comments.mapNotNull { it.comments?.trim() }.filter { it.isNotEmpty() }.distinct()) { c ->
                        Text(c, Modifier.fillMaxWidth().clickable { onPick(c) }.padding(vertical = 12.dp))
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = free.isNotBlank(), onClick = { onPick(free.trim()) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Close") } },
    )
}

/** Seat positions 1-16 (FrmPosition). */
@Composable
private fun PositionDialog(onPick: (String) -> Unit, onClose: () -> Unit) {
    val seats = remember { mutableStateListOf<Int>() }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Position (seat)") },
        text = {
            LazyVerticalGrid(GridCells.Fixed(4), Modifier.heightIn(max = 300.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items((1..16).toList()) { n ->
                    val on = n in seats
                    Box(
                        Modifier.height(48.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (on) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.secondaryContainer)
                            .clickable { if (on) seats.remove(n) else seats.add(n) },
                        contentAlignment = Alignment.Center,
                    ) { Text("$n", fontWeight = FontWeight.Bold, color = if (on) Color.White else MaterialTheme.colorScheme.onSecondaryContainer) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = seats.isNotEmpty(), onClick = { onPick(seats.sorted().joinToString(",")) }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Close") } },
    )
}

/** Item discount: set a new price (FrmKeyBoard_Disc_Item). */
@Composable
private fun PriceDialog(line: CartLine, onSet: (Double) -> Unit, onClose: () -> Unit) {
    var text by remember { mutableStateOf(money(line.price)) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(line.description) },
        text = {
            Column {
                Text("Actual price: ${money(line.aPrice)}")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("New price") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
        },
        confirmButton = {
            TextButton(enabled = text.toDoubleOrNull() != null, onClick = { onSet(text.toDouble()) }) { Text("Set price") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Close") } },
    )
}

/** Section discount in % (frmKeyBoard_Disc_Section). 0 % puts the actual prices back. */
@Composable
private fun SectionDiscountDialog(sections: List<String>, onApply: (Set<String>, Double) -> Unit, onClose: () -> Unit) {
    val chosen = remember { mutableStateListOf<String>() }
    var pct by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Section discount") },
        text = {
            Column {
                OutlinedTextField(value = pct, onValueChange = { pct = it }, label = { Text("Discount %") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    items(sections) { s ->
                        val on = s in chosen
                        Row(Modifier.fillMaxWidth().clickable { if (on) chosen.remove(s) else chosen.add(s) },
                            verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = on, onCheckedChange = { if (it) chosen.add(s) else chosen.remove(s) })
                            Text(s)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = chosen.isNotEmpty() && pct.toDoubleOrNull() != null,
                onClick = { onApply(chosen.toSet(), pct.toDouble()) }) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Close") } },
    )
}
