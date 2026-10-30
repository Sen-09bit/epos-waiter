@file:OptIn(ExperimentalMaterial3Api::class)

package com.epos.waiter.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.epos.waiter.AppViewModel
import com.epos.waiter.data.CartLine
import com.epos.waiter.data.MenuItem
import com.epos.waiter.data.money

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

@Composable
fun OrderScreen(vm: AppViewModel) {
    var selectedKey by remember { mutableStateOf<Long?>(null) }
    var dialog by remember { mutableStateOf<OrderDialog?>(null) }
    var tab by remember { mutableStateOf(0) }
    val wide = isWide()

    fun indexOf(key: Long?): Int = if (key == null) -1 else vm.lines.indexOfFirst { it.key == key }

    fun onItemTapped(item: MenuItem) {
        if (vm.busy) return // saving: don't change the order underneath it
        if (vm.comboSections(item).isNotEmpty()) {
            dialog = OrderDialog.Combo(item)
        } else {
            val i = vm.addItem(item)
            selectedKey = vm.lines.getOrNull(i)?.key
            if (vm.askPositionAfterItem && selectedKey != null) dialog = OrderDialog.Position(selectedKey!!)
        }
    }

    val leave: () -> Unit = { if (vm.changed) dialog = OrderDialog.ConfirmLeave else vm.leaveOrder() }
    BackHandler(enabled = dialog == null) { leave() }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            navigationIcon = {
                IconButton(onClick = leave) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to tables") }
            },
            title = {
                Column {
                    Text("Table ${vm.orderTable}", maxLines = 1)
                    val sub = listOfNotNull(
                        if (vm.covers > 0) "${vm.covers} covers" else null,
                        vm.customerType.ifBlank { null },
                    ).joinToString(" · ")
                    if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.labelMedium)
                }
            },
            colors = brandBar(), windowInsets = NoInsets,
        )

        if (wide) {
            Row(Modifier.fillMaxSize()) {
                MenuPane(vm, Modifier.weight(3f).fillMaxHeight(), ::onItemTapped)
                VerticalDivider()
                CartPane(vm, selectedKey, { selectedKey = it }, { dialog = it }, Modifier.weight(2f).fillMaxHeight())
            }
        } else {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Menu") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = {
                    Text("Order (${vm.editor.totalQty})  ${money(vm.editor.totalValue)}")
                })
            }
            if (tab == 0) {
                MenuPane(vm, Modifier.weight(1f), ::onItemTapped)
                // Quick summary bar so the waiter can jump to the order.
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${vm.editor.totalQty} items  ·  ${money(vm.editor.totalValue)}",
                        Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Button(onClick = { tab = 1 }) { Text("View order") }
                }
            } else {
                CartPane(vm, selectedKey, { selectedKey = it }, { dialog = it }, Modifier.weight(1f))
            }
        }
    }

    when (val d = dialog) {
        null -> Unit
        is OrderDialog.Combo -> ComboDialog(vm, d.item, onDone = { picks ->
            val i = vm.addCombo(d.item, 1, picks)
            selectedKey = vm.lines.getOrNull(i)?.key
            dialog = null
        }, onClose = { dialog = null })
        is OrderDialog.Note -> NoteDialog(vm, onPick = { text ->
            val i = indexOf(d.key)
            vm.edit { addNote(i, text) }
            dialog = null
        }, onClose = { dialog = null })
        is OrderDialog.Position -> PositionDialog(onPick = { seats ->
            val i = indexOf(d.key)
            vm.edit { addNote(i, "POS : $seats") }
            dialog = null
        }, onClose = { dialog = null })
        is OrderDialog.Price -> {
            val line = vm.lines.getOrNull(indexOf(d.key))
            if (line == null) dialog = null else PriceDialog(line, onSet = { p ->
                vm.edit { setPrice(indexOf(d.key), p) }
                dialog = null
            }, onClose = { dialog = null })
        }
        is OrderDialog.ConfirmDelete -> ConfirmDialog("Delete this item?", "It will be removed from the order.", "Delete",
            onYes = { vm.edit { delete(indexOf(d.key)) }; selectedKey = null; dialog = null }, onNo = { dialog = null })
        is OrderDialog.ConfirmCancel -> ConfirmDialog("Cancel this item?", "The kitchen will get a \"Cancel Item\" ticket with the next KOT.", "Cancel item",
            onYes = { vm.edit { cancel(indexOf(d.key)) }; dialog = null }, onNo = { dialog = null })
        OrderDialog.SectionDiscount -> SectionDiscountDialog(vm.editor.sectionsInOrder(), onApply = { sections, pct ->
            vm.edit { sectionDiscount(sections, pct) }
            dialog = null
        }, onClose = { dialog = null })
        OrderDialog.ConfirmLeave -> ConfirmDialog("Leave without sending?", "Changes not sent with KOT will be lost.", "Leave",
            onYes = { dialog = null; vm.leaveOrder() }, onNo = { dialog = null })
        OrderDialog.ConfirmInvoice -> ConfirmDialog("Print the bill?", "The order is saved and the bill is printed.", "Invoice",
            onYes = { dialog = null; vm.sendInvoice() }, onNo = { dialog = null })
    }
}

// ============================================================================ menu

@Composable
private fun MenuPane(vm: AppViewModel, modifier: Modifier, onTap: (MenuItem) -> Unit) {
    var section by remember { mutableStateOf(vm.sections.firstOrNull()?.name.orEmpty()) }
    var query by remember { mutableStateOf("") }
    Column(modifier) {
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, contentDescription = "Clear") }
            },
            placeholder = { Text("Search items") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        )
        val shown: List<MenuItem>
        if (query.isNotBlank()) {
            shown = vm.search(query)
        } else {
            LazyRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
            ) {
                items(vm.sections, key = { it.name }) { s ->
                    val logo = rememberBase64Image(s.logo)
                    FilterChip(
                        selected = section == s.name,
                        onClick = { section = s.name },
                        label = { Text(s.name) },
                        leadingIcon = if (logo != null) {
                            {
                                Image(logo, contentDescription = null, contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(22.dp).clip(RoundedCornerShape(4.dp)))
                            }
                        } else null,
                    )
                }
            }
            shown = vm.itemsOf(section)
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(128.dp),
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(shown, key = { it.code }) { item ->
                Card(
                    Modifier.fillMaxWidth().heightIn(min = 84.dp).clickable { onTap(item) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text(item.name, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.weight(1f, fill = false))
                        if (item.priceValue > 0) Text(money(item.priceValue), color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge)
                        if (vm.comboSections(item).isNotEmpty()) Text("Combo", color = MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

// ============================================================================ order (cart)

@Composable
private fun CartPane(
    vm: AppViewModel,
    selectedKey: Long?,
    select: (Long?) -> Unit,
    open: (OrderDialog) -> Unit,
    modifier: Modifier,
) {
    val editor = vm.editor
    val selected = vm.lines.firstOrNull { it.key == selectedKey }
    Column(modifier) {
        if (vm.lines.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No items yet. Tap items in the menu.", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                itemsIndexed(vm.lines, key = { _, l -> l.key }) { _, l ->
                    CartRow(l, l.key == selectedKey) { select(if (l.key == selectedKey) null else l.key) }
                    HorizontalDivider()
                }
            }
        }

        if (selected != null && !vm.busy) LineActions(selected, open, vm)

        HorizontalDivider()
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Items ${editor.totalQty}", Modifier.weight(1f))
            Text("Total ${money(editor.totalValue)}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { open(OrderDialog.SectionDiscount) }, enabled = vm.lines.isNotEmpty(),
                modifier = Modifier.height(56.dp)) { Text("% Disc") }
            Button(
                onClick = { vm.sendKot() }, enabled = vm.lines.isNotEmpty() && !vm.busy,
                modifier = Modifier.weight(1f).height(56.dp),
            ) { Text("KOT", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
            Button(
                onClick = { open(OrderDialog.ConfirmInvoice) }, enabled = vm.lines.isNotEmpty() && !vm.busy,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                modifier = Modifier.weight(1f).height(56.dp),
            ) { Text("INVOICE", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun CartRow(l: CartLine, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    val indent = if (l.isNote || l.isSubItem) 28.dp else 0.dp
    Row(
        Modifier.fillMaxWidth().background(bg).clickable(onClick = onClick).padding(start = 12.dp + indent, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!l.isNote && !l.isSubItem) {
            Text(l.qty.toString(), Modifier.width(36.dp), fontWeight = FontWeight.Bold,
                color = if (l.qty < 0) BilledRed else Color.Unspecified)
        }
        Column(Modifier.weight(1f)) {
            Text(
                l.shownText,
                fontStyle = if (l.isNote) FontStyle.Italic else FontStyle.Normal,
                color = when {
                    l.isCancelled -> BilledRed
                    l.isNote || l.isSubItem -> SentGrey
                    else -> Color.Unspecified
                },
                textDecoration = if (l.isCancelled && l.qty > 0) TextDecoration.LineThrough else null,
            )
            val tags = listOfNotNull(
                if (l.isSent) "sent" else "new",
                if (l.isCancelled && l.qty < 0) "cancel ticket" else null,
                if (l.price != l.aPrice && l.aPrice > 0) "was ${money(l.aPrice)}" else null,
            )
            if (!l.isNote && !l.isSubItem) Text(tags.joinToString(" · "), style = MaterialTheme.typography.labelSmall,
                color = if (l.isSent) SentGrey else MaterialTheme.colorScheme.primary)
        }
        if (!l.isNote && !l.isSubItem) Text(money(l.value), textAlign = TextAlign.End, modifier = Modifier.width(80.dp))
    }
}

/** Buttons for the selected line. Only the actions allowed for that line are shown. */
@Composable
private fun LineActions(l: CartLine, open: (OrderDialog) -> Unit, vm: AppViewModel) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer)
            .horizontalScroll(rememberScrollState()).padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val main = !l.isNote && !l.isSubItem && !l.isCancelled
        if (main && !l.isSent) {
            FilledTonalButton(onClick = { vm.edit { changeQty(vm.lines.indexOfFirst { it.key == l.key }, -1) } }) { Text("−", fontSize = 20.sp) }
            FilledTonalButton(onClick = { vm.edit { changeQty(vm.lines.indexOfFirst { it.key == l.key }, 1) } }) { Text("+", fontSize = 20.sp) }
        }
        if (main) {
            FilledTonalButton(onClick = { open(OrderDialog.Note(l.key)) }) { Text("Comment") }
            FilledTonalButton(onClick = { open(OrderDialog.Position(l.key)) }) { Text("Position") }
            if (l.price > 0) FilledTonalButton(onClick = { open(OrderDialog.Price(l.key)) }) { Text("Price") }
        }
        if (!l.isCancelled && !l.isSubItem) {
            FilledTonalButton(onClick = { open(OrderDialog.ConfirmDelete(l.key)) }) { Text("Delete") }
        }
        if (main && l.isSent && l.isCombo == 0) {
            FilledTonalButton(onClick = { open(OrderDialog.ConfirmCancel(l.key)) },
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = BilledRed, contentColor = Color.White)) { Text("Cancel item") }
        }
    }
}

// ============================================================================ dialogs

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
