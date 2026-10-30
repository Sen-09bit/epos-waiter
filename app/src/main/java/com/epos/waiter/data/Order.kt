package com.epos.waiter.data

import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToLong

private val keys = AtomicLong(1)

/**
 * One line of a table's order. Same columns as the KOT table / frmKOT grid:
 *  - isCombo 0 = normal, 1..1000 = combo main item, >1000 = combo sub item of main (isCombo - 1000)
 *  - description starting with "----" = comment or position line
 *  - mode 1 = cancelled (the original line, plus a minus-quantity line that prints "Cancel Item")
 *  - isPrint 1 = already sent to the kitchen
 */
data class CartLine(
    val itemCode: Int,
    val description: String,
    val aPrice: Double,
    val price: Double,
    val qty: Int,
    val value: Double,
    val isPrint: Int,
    val isBill: Int,
    val isCombo: Int,
    val mode: Int,
    val sBill: Int,
    val invNo: Int,
    val courseType: String,
    val ordering: Int,
    val section: String,
    val department: String,
    val printerName: String,
    /** true when the line came from the server (getKOT); new lines are false until saved. */
    val saved: Boolean = false,
    val key: Long = keys.getAndIncrement(),
) {
    val isNote: Boolean get() = description.startsWith("----")
    val isSubItem: Boolean get() = isCombo > 1000
    val isComboMain: Boolean get() = isCombo in 1..1000
    val isCancelled: Boolean get() = mode == 1
    /** Printed in the kitchen. Items marked "do not print" (IsPrint = 1 in M_Items) only count once saved. */
    val isSent: Boolean get() = saved && isPrint == 1
    val shownText: String
        get() = when {
            isNote -> description.removePrefix("----")
            else -> description
        }
}

fun money(v: Double): String = String.format(java.util.Locale.US, "%.2f", v)

private fun round2(v: Double): Double = (v * 100.0).roundToLong() / 100.0

/**
 * Order editing rules, taken from frmKOT.vb (lbl_Item_click, Load_Items_Grid, btnDeleteItem_Click,
 * btnCancelItem_Click, lbl_Comment_click, btnPosition_Click, btnDiscount_*).
 * Every function returns null when done, or a message to show.
 */
class OrderEditor(private val lines: MutableList<CartLine>, private val userCategory: Int) {

    val isAdmin: Boolean get() = userCategory == 1
    val hasBill: Boolean get() = lines.any { it.invNo > 0 }
    private val firstInvNo: Int get() = lines.filter { it.invNo > 0 }.minOfOrNull { it.invNo } ?: 0

    val totalQty: Int get() = lines.filter { !it.isSubItem && !it.isNote }.sumOf { it.qty }
    val totalValue: Double get() = round2(lines.sumOf { it.value })
    val hasUnsent: Boolean get() = lines.any { !it.isSent }

    private fun fromItem(item: MenuItem, qty: Int, price: Double, isBill: Int, isCombo: Int, description: String) = CartLine(
        itemCode = item.code,
        description = description,
        aPrice = price,
        price = price,
        qty = qty,
        value = round2(price * qty),
        isPrint = (item.isPrint ?: 0.0).toInt(), // items marked "do not print" in M_Items never go to the kitchen
        isBill = isBill,
        isCombo = isCombo,
        mode = 0,
        sBill = 1,
        invNo = firstInvNo,
        courseType = item.courseType?.trim().orEmpty(),
        ordering = (item.ordering ?: 0.0).toInt(),
        section = item.sectionName,
        department = item.department?.trim().orEmpty(),
        printerName = item.printerName?.trim().orEmpty(),
    )

    /** Adds a normal item. Tapping the same item again adds to the last unsent line of it. Returns its index. */
    fun addItem(item: MenuItem, qty: Int = 1): Int {
        val last = lines.indexOfLast { it.itemCode == item.code && !it.isNote && it.isCombo == 0 && !it.isCancelled }
        if (last >= 0) {
            val l = lines[last]
            val nextIsNote = last + 1 < lines.size && lines[last + 1].isNote
            if (!l.saved && l.price == l.aPrice && !nextIsNote && last == lines.lastIndex) {
                lines[last] = l.copy(qty = l.qty + qty, value = round2(l.price * (l.qty + qty)))
                return last
            }
        }
        lines.add(fromItem(item, qty, item.priceValue, 1, 0, item.name))
        return lines.lastIndex
    }

    /** Combo: main item + one chosen item per sub-section. Sub item prices are added to the main item. */
    fun addCombo(item: MenuItem, qty: Int, choices: List<MenuItem>): Int {
        val used = lines.filter { it.isComboMain }.map { it.isCombo }.toSet()
        var combo = lines.size + 1
        while (combo in used) combo++
        if (combo > 1000) combo = (1..1000).first { it !in used }
        val price = item.priceValue + choices.sumOf { it.subPriceValue }
        lines.add(fromItem(item, qty, price, 1, combo, item.name))
        val mainIndex = lines.lastIndex
        for (c in choices) {
            lines.add(fromItem(c, 0, 0.0, 0, combo + 1000, "**" + c.name))
        }
        return mainIndex
    }

    fun canAddNote(index: Int): String? {
        val l = lines.getOrNull(index) ?: return "Select an item first."
        if (l.isNote) return "Select the item, not the comment."
        if (l.isSubItem) return "Select the main combo item."
        if (l.isCancelled) return "This item is cancelled."
        return null
    }

    /** Comment ("----text") or position ("----POS : 1,2") right after the item, like ePOS.exe. */
    fun addNote(index: Int, text: String): String? {
        canAddNote(index)?.let { return it }
        val l = lines[index]
        val note = CartLine(
            itemCode = l.itemCode, description = "----" + text.trim(), aPrice = 0.0, price = 0.0, qty = 0, value = 0.0,
            isPrint = 0, isBill = 0, isCombo = 0, mode = 0, sBill = l.sBill, invNo = l.invNo,
            courseType = l.courseType, ordering = l.ordering, section = l.section,
            department = l.department, printerName = l.printerName,
        )
        lines.add(index + 1, note)
        return null
    }

    fun changeQty(index: Int, delta: Int): String? {
        val l = lines.getOrNull(index) ?: return null
        if (l.saved) return "Already saved. Cancel it and add again."
        if (l.isNote || l.isSubItem || l.isCancelled) return null
        val q = l.qty + delta
        if (q < 1) return "Use Delete to remove the item."
        lines[index] = l.copy(qty = q, value = round2(l.price * q))
        return null
    }

    /** "Another billed line on the same invoice remains" - frmKOT's IsAllow check after a bill. */
    private fun allowedAfterBill(index: Int): Boolean {
        if (!hasBill) return true
        val inv = lines[index].invNo
        return lines.indices.any { it != index && lines[it].isBill == 1 && lines[it].invNo == inv }
    }

    fun delete(index: Int): String? {
        val l = lines.getOrNull(index) ?: return null
        if (l.isCancelled) return "Can't delete a cancelled item."
        if (l.isSubItem) return "Can't delete a combo sub item. Delete the main item."
        if (hasBill && !isAdmin) return "Bill already prepared. Can't delete."
        if (l.isSent && !l.isNote && !isAdmin) return "Already sent to the kitchen. Use Cancel instead."
        if (!allowedAfterBill(index)) return "Can't delete this item."

        val removeKeys = mutableSetOf(l.key)
        if (l.isComboMain) lines.filter { it.isCombo == l.isCombo + 1000 }.forEach { removeKeys.add(it.key) }
        if (!l.isNote) {
            var i = index + 1
            while (i < lines.size && lines[i].isNote && lines[i].itemCode == l.itemCode) {
                removeKeys.add(lines[i].key); i++
            }
        }
        lines.removeAll { it.key in removeKeys }
        return null
    }

    /** Marks the item cancelled and adds a minus line so the kitchen gets a "Cancel Item" ticket. */
    fun cancel(index: Int): String? {
        val l = lines.getOrNull(index) ?: return null
        if (l.isCancelled) return "Already cancelled."
        if (l.isNote) return "Use Delete for comments."
        if (l.isCombo != 0) return "Can't cancel combo items. Use Delete."
        if (l.qty <= 0) return "Check item quantity."
        if (hasBill && !isAdmin) return "Bill already prepared. Can't cancel."
        if (!allowedAfterBill(index)) return "Can't cancel this item."
        lines[index] = l.copy(mode = 1, isBill = 0)
        lines.add(
            l.copy(
                qty = -l.qty, value = -l.value, isPrint = 0, isBill = 0, mode = 1, saved = false,
                key = keys.getAndIncrement(),
            )
        )
        return null
    }

    fun setPrice(index: Int, newPrice: Double): String? {
        val l = lines.getOrNull(index) ?: return null
        if (l.isCancelled) return "Can't discount a cancelled item."
        if (l.price <= 0.0) return "Check price."
        if (newPrice < 0.0) return "Enter a valid price."
        lines[index] = l.copy(price = round2(newPrice), value = round2(newPrice * l.qty))
        return null
    }

    /** Section discount: price = actual price - pct %, for every line of those sections. */
    fun sectionDiscount(sections: Set<String>, percent: Double): String? {
        if (percent < 0.0 || percent > 100.0) return "Enter a percentage between 0 and 100."
        if (sections.isEmpty()) return "Select a section."
        for (i in lines.indices) {
            val l = lines[i]
            if (l.section in sections && !l.isCancelled && l.aPrice > 0.0) {
                val p = round2(l.aPrice - round2(l.aPrice * percent / 100.0))
                lines[i] = l.copy(price = p, value = round2(p * l.qty))
            }
        }
        return null
    }

    fun sectionsInOrder(): List<String> =
        lines.filter { !it.isSubItem && !it.isNote && it.section.isNotEmpty() }.map { it.section }.distinct()

    companion object {
        fun fromKot(rows: List<KotRow>): List<CartLine> = rows.sortedBy { it.sNo ?: 0.0 }.map { r ->
            CartLine(
                itemCode = (r.itemCode ?: 0.0).toInt(),
                description = r.description?.trim().orEmpty(),
                aPrice = r.aPrice ?: 0.0,
                price = r.price ?: 0.0,
                qty = (r.qty ?: 0.0).toInt(),
                value = round2((r.price ?: 0.0) * (r.qty ?: 0.0)),
                isPrint = (r.isPrint ?: 0.0).toInt(),
                isBill = (r.isBill ?: 0.0).toInt(),
                isCombo = (r.isCombo ?: 0.0).toInt(),
                mode = (r.mode ?: 0.0).toInt(),
                sBill = (r.sBill ?: 0.0).toInt(),
                invNo = (r.invNo ?: 0.0).toInt(),
                courseType = r.courseType?.trim().orEmpty(),
                ordering = (r.ordering ?: 0.0).toInt(),
                section = r.section?.trim().orEmpty(),
                department = r.department?.trim().orEmpty(),
                printerName = r.printerName?.trim().orEmpty(),
                saved = true,
            )
        }

        fun toDto(l: CartLine, room: String, table: String, covers: Int, customerType: String) = KotLineDto(
            itemCode = l.itemCode, description = l.description, aPrice = l.aPrice, price = l.price, qty = l.qty,
            value = l.value, isPrint = l.isPrint, isBill = l.isBill, isCombo = l.isCombo, mode = l.mode,
            sBill = l.sBill, invNo = l.invNo, courseType = l.courseType, ordering = l.ordering, section = l.section,
            department = l.department, printerName = l.printerName, cType = customerType, covers = covers.toString(),
            room = room, tableNo = table,
        )
    }
}
