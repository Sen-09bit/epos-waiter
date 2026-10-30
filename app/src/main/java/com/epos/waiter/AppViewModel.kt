package com.epos.waiter

import android.app.Application
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.epos.waiter.data.CartLine
import com.epos.waiter.data.Comment
import com.epos.waiter.data.Concern
import com.epos.waiter.data.Discovery
import com.epos.waiter.data.EposApi
import com.epos.waiter.data.Expense
import com.epos.waiter.data.FoundServer
import com.epos.waiter.data.KotRequest
import com.epos.waiter.data.MenuItem
import com.epos.waiter.data.OrderEditor
import com.epos.waiter.data.Prefs
import com.epos.waiter.data.Room
import com.epos.waiter.data.Section
import com.epos.waiter.data.ServerClient
import com.epos.waiter.data.SubSection
import com.epos.waiter.data.TableInfo
import com.epos.waiter.data.TableStatus
import com.epos.waiter.data.User
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

enum class Screen { Connect, Login, Tables, Order, Expenses, Profile, Password }

/** Asked before opening a free table (old app: number of covers + customer type). */
data class CoversRequest(val table: TableInfo)

/** Shown when another phone or the PC has the table open. */
data class TableBusy(val table: String, val lines: List<String>)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = Prefs(app)
    private val deviceName: String = (Build.MANUFACTURER + " " + Build.MODEL).trim()

    // ---------------------------------------------------------------- general UI state
    var screen by mutableStateOf(Screen.Connect)
        private set
    var busy by mutableStateOf(false)
        private set
    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages = messageChannel.receiveAsFlow()

    private fun say(text: String) {
        if (text.isNotBlank()) messageChannel.trySend(text)
    }

    // ---------------------------------------------------------------- connection
    var serverAddress by mutableStateOf(prefs.serverAddress)
        private set
    var scanning by mutableStateOf(false)
        private set
    val foundServers = mutableStateListOf<FoundServer>()

    private val api: EposApi get() = ServerClient.api(ServerClient.baseUrlFor(serverAddress))

    /** Runs a server call with the busy spinner; shows a friendly message on failure. */
    private fun call(block: suspend () -> Unit) {
        if (busy) {
            say("Please wait..."); return
        }
        viewModelScope.launch {
            busy = true
            try {
                block()
            } catch (e: Exception) {
                say(ServerClient.friendly(e))
            } finally {
                busy = false
            }
        }
    }

    fun scan() {
        if (scanning) return
        viewModelScope.launch {
            scanning = true
            foundServers.clear()
            try {
                val found = Discovery.scan(getApplication())
                foundServers.addAll(found)
                if (found.isEmpty()) say("No ePOS computer found on this Wi-Fi. Type its address below.")
                else if (found.size == 1 && screen == Screen.Connect) connect(found[0].address)
            } catch (e: Exception) {
                say(ServerClient.friendly(e))
            } finally {
                scanning = false
            }
        }
    }

    private var connectJob: Job? = null

    fun connect(address: String, silentFail: Boolean = false) {
        val a = address.trim()
        if (a.isEmpty()) {
            say("Enter the address shown in the ePOS Mobile Server window."); return
        }
        if (connectJob?.isActive == true) return
        connectJob = viewModelScope.launch {
            busy = true
            try {
                val probe = ServerClient.api(ServerClient.baseUrlFor(a)).getUsers()
                if (!probe.ok) throw IllegalStateException(probe.text.ifBlank { "Server did not accept the request." })
                serverAddress = a
                prefs.serverAddress = a
                loadMasterData()
            } catch (e: Exception) {
                screen = Screen.Connect
                if (silentFail) scanAfterFailure() else say(ServerClient.friendly(e))
            } finally {
                busy = false
            }
        }
    }

    private fun scanAfterFailure() {
        say("Could not reach $serverAddress. Searching the Wi-Fi...")
        scan()
    }

    fun forgetServer() {
        screen = Screen.Connect
        user = null
        scan()
    }

    // ---------------------------------------------------------------- master data (getItems.php)
    val users = mutableStateListOf<User>()
    var concern by mutableStateOf<Concern?>(null)
        private set
    private var items: List<MenuItem> = emptyList()
    var sections by mutableStateOf<List<Section>>(emptyList())
        private set
    private var subSections: List<SubSection> = emptyList()
    var rooms by mutableStateOf<List<Room>>(emptyList())
        private set
    var comments by mutableStateOf<List<Comment>>(emptyList())
        private set
    val lastUserId: Int get() = prefs.lastUserId

    private suspend fun loadMasterData() {
        val m = api.getMasterData()
        if (!m.ok) throw IllegalStateException(m.text)
        users.clear(); users.addAll(m.userList.orEmpty().distinctBy { it.id })
        concern = m.concernList?.firstOrNull()
        items = m.itemList.orEmpty().distinctBy { it.code }
        sections = m.sectionList.orEmpty().filter { it.isMain && it.name.isNotEmpty() }.distinctBy { it.name }
        subSections = m.subSectionList.orEmpty()
        val roomResponse = runCatching { api.getRooms() }.getOrNull()
        rooms = (roomResponse?.roomList ?: m.roomList).orEmpty().filter { it.id.isNotEmpty() }.distinctBy { it.id }
        comments = runCatching { api.getComments().commentList.orEmpty() }.getOrDefault(emptyList())
        screen = Screen.Login
    }

    fun reloadMenu() = call {
        loadMasterData()
        screen = if (user != null) Screen.Tables else Screen.Login
        say("Menu updated")
    }

    fun itemsOf(section: String): List<MenuItem> =
        items.filter { it.visible && it.sectionName == section }.sortedBy { it.name.lowercase() }

    fun search(text: String): List<MenuItem> {
        val t = text.trim().lowercase()
        if (t.isEmpty()) return emptyList()
        return items.filter { it.visible && it.name.lowercase().contains(t) }.sortedBy { it.name.lowercase() }.take(60)
    }

    /** Sections a combo item asks for (empty = not a combo). */
    fun comboSections(item: MenuItem): List<String> =
        subSections.filter { (it.itemCode ?: 0.0).toInt() == item.code }.mapNotNull { it.section?.trim() }
            .filter { it.isNotEmpty() }.distinct()

    fun comboChoices(section: String): List<MenuItem> =
        items.filter { it.sectionName == section && it.visibleAsSubItem }.sortedBy { it.code }

    // ---------------------------------------------------------------- login
    var user by mutableStateOf<User?>(null)
        private set

    fun login(u: User, password: String) {
        // Same check as the old app: the password is compared on the phone.
        if (password.trim() != u.password?.trim().orEmpty()) {
            say("Password mismatches"); return
        }
        user = u
        prefs.lastUserId = u.id
        val start = rooms.firstOrNull { it.id == prefs.lastRoom } ?: rooms.firstOrNull()
        currentRoom = start
        screen = Screen.Tables
        refreshTables()
    }

    fun logout() {
        user = null
        screen = Screen.Login
    }

    // ---------------------------------------------------------------- tables
    var currentRoom by mutableStateOf<Room?>(null)
        private set
    var tables by mutableStateOf<List<TableInfo>>(emptyList())
        private set
    var coversRequest by mutableStateOf<CoversRequest?>(null)
        private set
    var tableBusy by mutableStateOf<TableBusy?>(null)
        private set

    fun selectRoom(r: Room) {
        currentRoom = r
        prefs.lastRoom = r.id
        tables = emptyList()
        refreshTables()
    }

    private var tablesJob: Job? = null

    fun refreshTables(quiet: Boolean = false) {
        val room = currentRoom ?: return
        tablesJob?.cancel()
        tablesJob = viewModelScope.launch {
            try {
                val res = api.getTables(room.id)
                if (currentRoom?.id != room.id) return@launch
                if (res.ok) tables = res.tableList.orEmpty().distinctBy { it.number } else if (!quiet) say(res.text)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!quiet) say(ServerClient.friendly(e))
            }
        }
    }

    /** Tap on a table: check the lock, lock it, then ask covers (free) or load the order (blue/red). */
    fun openTable(t: TableInfo) = call {
        val room = currentRoom ?: return@call
        val u = user ?: return@call
        val status = api.checkTable(room.id, t.number)
        if (!status.ok) {
            say(status.text.ifBlank { "Could not check the table." }); return@call
        }
        val allowed = status.lockStatus?.firstOrNull()?.type?.trim().equals("ALLOW", ignoreCase = true)
        if (!allowed) {
            val who = status.lockDetails.orEmpty().flatMap { listOfNotNull(it.user, it.mode, it.name) }.filter { it.isNotBlank() }
            tableBusy = TableBusy(t.number, who.ifEmpty { listOf("The table is open on another device.") })
            return@call
        }
        val lock = api.tableLock("Insert", room.id, t.number, u.name, "Mobile", deviceName)
        if (!lock.ok) {
            say(lock.text.ifBlank { "Could not open the table." }); return@call
        }
        try {
            // Always read the saved order: the colour on screen may be up to 15 seconds old.
            val kot = api.getKot(room.id, t.number)
            if (!kot.ok) {
                unlock(room.id, t.number); say(kot.text); return@call
            }
            val rows = kot.kotList.orEmpty()
            if (rows.isEmpty()) {
                coversRequest = CoversRequest(t)
            } else {
                startOrder(t, covers = rows.maxOfOrNull { (it.covers ?: 0.0).toInt() } ?: 0,
                    customerType = rows.firstOrNull { !it.cType.isNullOrBlank() }?.cType?.trim().orEmpty(),
                    lines = OrderEditor.fromKot(rows))
            }
        } catch (e: Exception) {
            unlock(room.id, t.number)
            throw e
        }
    }

    fun dismissTableBusy() {
        tableBusy = null
    }

    fun coversChosen(covers: Int, customerType: String) {
        val req = coversRequest ?: return
        coversRequest = null
        startOrder(req.table, covers, customerType, emptyList())
    }

    fun coversCancelled() {
        val req = coversRequest ?: return
        coversRequest = null
        val room = currentRoom ?: return
        viewModelScope.launch { unlock(room.id, req.table.number) }
    }

    private suspend fun unlock(room: String, table: String) {
        runCatching { api.tableLock("Delete", room, table, user?.name.orEmpty(), "Mobile", deviceName) }
    }

    // ---------------------------------------------------------------- order
    var orderTable by mutableStateOf("")
        private set
    var orderRoom by mutableStateOf("")
        private set
    var covers by mutableIntStateOf(0)
        private set
    var customerType by mutableStateOf("")
        private set
    val lines = mutableStateListOf<CartLine>()
    var changed by mutableStateOf(false)
        private set

    val editor: OrderEditor get() = OrderEditor(lines, (user?.category ?: 0.0).toInt())

    private fun startOrder(t: TableInfo, covers: Int, customerType: String, lines: List<CartLine>) {
        orderRoom = currentRoom?.id.orEmpty()
        orderTable = t.number
        this.covers = covers
        this.customerType = customerType
        this.lines.clear()
        this.lines.addAll(lines)
        changed = false
        screen = Screen.Order
    }

    /** Applies an edit; shows its message if it was refused. */
    fun edit(action: OrderEditor.() -> String?) {
        val msg = editor.action()
        if (msg != null) say(msg) else changed = true
    }

    fun addItem(item: MenuItem): Int {
        val i = editor.addItem(item)
        changed = true
        return i
    }

    fun addCombo(item: MenuItem, qty: Int, choices: List<MenuItem>): Int {
        val i = editor.addCombo(item, qty, choices)
        changed = true
        return i
    }

    val askPositionAfterItem: Boolean get() = (concern?.isPosition ?: 0.0).toInt() == 1

    /** KOT button: save and print in the kitchen (is_kot = 0). */
    fun sendKot() = save(isKot = 0)

    /** INVOICE button: save (is_kot = 1), then ask the server for the bill. */
    fun sendInvoice() = save(isKot = 1)

    private fun save(isKot: Int) {
        if (lines.isEmpty()) {
            say("No items selected"); return
        }
        if (busy) {
            say("Please wait..."); return
        }
        viewModelScope.launch {
            busy = true
            try {
                val body = KotRequest(
                    lines = lines.map { OrderEditor.toDto(it, orderRoom, orderTable, covers, customerType) },
                    incharge = user?.name.orEmpty(),
                    isKot = isKot,
                )
                val res = try {
                    api.saveOrder(body)
                } catch (e: Exception) {
                    // The server may have saved it before the connection broke: reopen the table to check.
                    say(ServerClient.friendly(e) + " Open the table again to check what was saved.")
                    closeOrder(unlockTable = true)
                    return@launch
                }
                if (!res.ok) {
                    say(res.text.ifBlank { "Could not save the order" })
                    // "KOT saved, but printing failed" = saved on the server; anything else = nothing saved, stay here.
                    if (res.text.startsWith("KOT saved", ignoreCase = true)) closeOrder(unlockTable = false)
                    return@launch
                }
                if (isKot == 1) {
                    val bill = try {
                        api.makeBill(orderRoom, orderTable)
                    } catch (e: Exception) {
                        null
                    }
                    say(bill?.text?.ifBlank { null } ?: "Order saved. The bill could not be confirmed - check on the ePOS computer.")
                } else {
                    say(res.text.ifBlank { "KOT sent" })
                }
                // Like the old app: back to the tables (the server already released the table).
                closeOrder(unlockTable = false)
            } finally {
                busy = false
            }
        }
    }

    /** Leave without sending. */
    fun leaveOrder() = call { closeOrder(unlockTable = true) }

    private suspend fun closeOrder(unlockTable: Boolean) {
        if (unlockTable) unlock(orderRoom, orderTable)
        lines.clear()
        changed = false
        screen = Screen.Tables
        refreshTables(quiet = true)
    }

    // ---------------------------------------------------------------- change / join tables
    var tableChoices by mutableStateOf<List<TableInfo>>(emptyList())
        private set

    fun loadChangeTableChoices() = loadChoices { api.getChangeTableList(it).let { r -> r to r.changeTableList } }

    fun loadJoinTableChoices() = loadChoices { api.getJoinTableList(it).let { r -> r to r.joinTableList } }

    private fun loadChoices(fetch: suspend (String) -> Pair<com.epos.waiter.data.ServerResponse, List<TableInfo>?>) {
        val room = currentRoom ?: return
        tableChoices = emptyList()
        viewModelScope.launch {
            try {
                val (res, list) = fetch(room.id)
                if (res.ok) tableChoices = list.orEmpty().distinctBy { it.number } else say(res.text)
            } catch (e: Exception) {
                say(ServerClient.friendly(e))
            }
        }
    }

    fun changeTable(source: String, destination: String, done: () -> Unit) = call {
        val room = currentRoom ?: return@call
        val res = api.changeTable(user?.name.orEmpty(), room.id, source, destination)
        say(res.text.ifBlank { if (res.ok) "Table changed" else "Could not change table" })
        if (res.ok) {
            done(); refreshTables(quiet = true)
        }
    }

    fun joinTables(source: String, others: List<String>, done: () -> Unit) = call {
        val room = currentRoom ?: return@call
        val res = api.joinTables(room.id, source, others.joinToString(","))
        say(res.text.ifBlank { if (res.ok) "Tables joined" else "Could not join tables" })
        if (res.ok) {
            done(); refreshTables(quiet = true)
        }
    }

    // ---------------------------------------------------------------- expenses / profile / password
    var expenses by mutableStateOf<List<Expense>>(emptyList())
        private set

    fun go(s: Screen) {
        screen = s
        when (s) {
            Screen.Expenses -> loadExpenses()
            Screen.Profile -> loadProfile()
            Screen.Tables -> refreshTables(quiet = true)
            else -> Unit
        }
    }

    private fun loadExpenses() = call {
        val res = api.getExpenses()
        if (res.ok) expenses = res.expenseList.orEmpty() else say(res.text)
    }

    fun addExpense(description: String, amount: String, done: () -> Unit) {
        if (description.isBlank()) {
            say("Enter a description"); return
        }
        if ((amount.toDoubleOrNull() ?: 0.0) <= 0.0) {
            say("Enter the amount"); return
        }
        call {
            val res = api.insertExpense(description.trim(), amount.trim())
            say(res.text.ifBlank { if (res.ok) "Expense saved" else "Could not save" })
            if (res.ok) {
                done()
                api.getExpenses().let { if (it.ok) expenses = it.expenseList.orEmpty() }
            }
        }
    }

    private fun loadProfile() = call {
        val res = api.getConcerns()
        if (res.ok) res.concernList?.firstOrNull()?.let { concern = it } else say(res.text)
    }

    fun saveProfile(c: Concern) = call {
        val res = api.updateProfile(
            conCode = (c.conCode ?: 1.0).toInt().toString(), name = c.concernName.orEmpty(), subTitle = c.subTitle.orEmpty(),
            add1 = c.add1.orEmpty(), add2 = c.add2.orEmpty(), city = c.city.orEmpty(), zip = c.zipCode.orEmpty(),
            country = c.country.orEmpty(), phone = c.phone.orEmpty(), email = c.email.orEmpty(),
            website = c.website.orEmpty(), vatNo = c.vatNo.orEmpty(), mobile = c.mobileNo.orEmpty(),
        )
        say(res.text.ifBlank { if (res.ok) "Profile updated" else "Could not update" })
        if (res.ok) {
            concern = c; screen = Screen.Tables
        }
    }

    fun changePassword(old: String, new: String, confirm: String) {
        val u = user ?: return
        when {
            old.trim() != u.password?.trim().orEmpty() -> say("Old password is incorrect")
            new.trim().isEmpty() -> say("Enter new password")
            new.trim() != confirm.trim() -> say("New passwords do not match")
            else -> call {
                val res = api.changePassword(u.id.toString(), new.trim())
                say(res.text.ifBlank { if (res.ok) "Password changed" else "Could not change password" })
                if (res.ok) {
                    val updated = u.copy(password = new.trim())
                    user = updated
                    val i = users.indexOfFirst { it.id == u.id }
                    if (i >= 0) users[i] = updated
                    screen = Screen.Tables
                }
            }
        }
    }

    /** Screen-specific back button. Returns false when the app should close. */
    fun back(): Boolean {
        when (screen) {
            Screen.Connect -> return false
            Screen.Login -> return false
            Screen.Tables -> return false
            Screen.Order -> Unit // the order screen asks before leaving
            Screen.Expenses, Screen.Profile, Screen.Password -> screen = Screen.Tables
        }
        return true
    }

    // Declared last so every property above is ready before it runs.
    init {
        // Reconnect to the last server straight away; otherwise search the Wi-Fi.
        if (serverAddress.isNotBlank()) connect(serverAddress, silentFail = true) else scan()
    }
}
