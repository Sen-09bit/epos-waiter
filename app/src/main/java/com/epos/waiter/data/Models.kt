package com.epos.waiter.data

import com.google.gson.annotations.SerializedName

/*
 * JSON models for the ePOS Mobile Server (same answers the old PHP gave).
 * Every field is nullable: Gson does not apply Kotlin defaults, and the database can return NULL.
 * Numbers are Double so whole and decimal values both parse.
 */

data class ServerResponse(
    @SerializedName("success") val success: Double? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("ConcernList") val concernList: List<Concern>? = null,
    @SerializedName("UserList") val userList: List<User>? = null,
    @SerializedName("ItemList") val itemList: List<MenuItem>? = null,
    @SerializedName("SectionList") val sectionList: List<Section>? = null,
    @SerializedName("SubSectionList") val subSectionList: List<SubSection>? = null,
    @SerializedName("RoomList") val roomList: List<Room>? = null,
    @SerializedName("TableList") val tableList: List<TableInfo>? = null,
    @SerializedName("changTableList") val changeTableList: List<TableInfo>? = null,
    @SerializedName("joinTableList") val joinTableList: List<TableInfo>? = null,
    @SerializedName("CommentList") val commentList: List<Comment>? = null,
    @SerializedName("ExpenseList") val expenseList: List<Expense>? = null,
    @SerializedName("KotList") val kotList: List<KotRow>? = null,
    @SerializedName("netAmtList") val lockStatus: List<LockStatus>? = null,
    @SerializedName("itemDetailList") val lockDetails: List<LockDetail>? = null,
    @SerializedName("name") val serverName: String? = null,
) {
    /** 1 = OK. 0 = error (message explains). */
    val code: Int get() = success?.toInt() ?: 0
    val ok: Boolean get() = code == 1
    val text: String get() = message?.trim().orEmpty()
}

data class Concern(
    @SerializedName("ConCode") val conCode: Double? = null,
    @SerializedName("ConcernName") val concernName: String? = null,
    @SerializedName("SubTitle") val subTitle: String? = null,
    @SerializedName("Add1") val add1: String? = null,
    @SerializedName("Add2") val add2: String? = null,
    @SerializedName("City") val city: String? = null,
    @SerializedName("ZipCode") val zipCode: String? = null,
    @SerializedName("Country") val country: String? = null,
    @SerializedName("Phone") val phone: String? = null,
    @SerializedName("E_mail") val email: String? = null,
    @SerializedName("Website") val website: String? = null,
    @SerializedName("VATNo") val vatNo: String? = null,
    @SerializedName("MobileNo") val mobileNo: String? = null,
    @SerializedName("IsPosition") val isPosition: Double? = null,
    @SerializedName("Logo") val logo: String? = null,
)

data class User(
    @SerializedName("UserId") val userId: Double? = null,
    @SerializedName("UserName") val userName: String? = null,
    @SerializedName("Password") val password: String? = null,
    @SerializedName("Category") val category: Double? = null,
    @SerializedName("Image") val image: String? = null,
) {
    val id: Int get() = userId?.toInt() ?: 0
    val name: String get() = userName?.trim().orEmpty()
}

data class MenuItem(
    @SerializedName("ItemCode") val itemCode: Double? = null,
    @SerializedName("Description") val description: String? = null,
    @SerializedName("CourseType") val courseType: String? = null,
    @SerializedName("Section") val section: String? = null,
    @SerializedName("Department") val department: String? = null,
    @SerializedName("Price") val price: Double? = null,
    @SerializedName("Sub_Price") val subPrice: Double? = null,
    @SerializedName("IsPrint") val isPrint: Double? = null,
    @SerializedName("Show_Items") val showItems: Double? = null,
    @SerializedName("Show_SubItems") val showSubItems: Double? = null,
    @SerializedName("Ordering") val ordering: Double? = null,
    @SerializedName("PrinterName") val printerName: String? = null,
) {
    val code: Int get() = itemCode?.toInt() ?: 0
    val name: String get() = description?.trim().orEmpty()
    val sectionName: String get() = section?.trim().orEmpty()
    val priceValue: Double get() = price ?: 0.0
    val subPriceValue: Double get() = subPrice ?: 0.0
    val visible: Boolean get() = (showItems ?: 0.0).toInt() == 1
    val visibleAsSubItem: Boolean get() = (showSubItems ?: 0.0).toInt() == 1
}

data class Section(
    @SerializedName("Code") val code: Double? = null,
    @SerializedName("Section") val section: String? = null,
    @SerializedName("Logo") val logo: String? = null,
    @SerializedName("IsSub_Section") val isSubSection: Double? = null,
    @SerializedName("ItemCount") val itemCount: Double? = null,
) {
    val name: String get() = section?.trim().orEmpty()
    val isMain: Boolean get() = (isSubSection ?: 0.0).toInt() == 0
}

/** M_Items_Sub: a combo item asks for one choice from each of these sections. */
data class SubSection(
    @SerializedName("ItemCode") val itemCode: Double? = null,
    @SerializedName("Section") val section: String? = null,
)

data class Room(
    @SerializedName("Code") val code: Double? = null,
    @SerializedName("Room_No") val roomNo: String? = null,
    @SerializedName("Description") val description: String? = null,
) {
    val id: String get() = roomNo?.trim().orEmpty()
    val label: String get() = description?.trim()?.takeIf { it.isNotEmpty() } ?: id
}

data class TableInfo(
    @SerializedName("Room_No") val roomNo: String? = null,
    @SerializedName("Table_No") val tableNo: String? = null,
    @SerializedName("Display_TableNo") val displayTableNo: String? = null,
    @SerializedName("Color_Status") val colorStatus: String? = null,
    // Extra details from getTablesPlan.php (missing with an older server):
    @SerializedName("X_POS") val xPos: Double? = null,
    @SerializedName("Y_POS") val yPos: Double? = null,
    @SerializedName("Covers") val covers: Double? = null,
    @SerializedName("Amount") val amount: Double? = null,
    @SerializedName("Minutes") val minutes: Double? = null,
    @SerializedName("LockedBy") val lockedBy: String? = null,
) {
    /** The number staff see and the key used everywhere (e.g. "5" or split table "5-1"). */
    val number: String get() = displayTableNo?.trim()?.takeIf { it.isNotEmpty() } ?: tableNo?.trim().orEmpty()
    val status: TableStatus
        get() = when (colorStatus?.trim()?.uppercase()) {
            "BLUE" -> TableStatus.Ordered
            "RED" -> TableStatus.Billed
            else -> TableStatus.Free
        }
}

enum class TableStatus { Free, Ordered, Billed }

data class Comment(
    @SerializedName("Code") val code: Double? = null,
    @SerializedName("Comments") val comments: String? = null,
)

data class PhpDate(@SerializedName("date") val date: String? = null)

data class Expense(
    @SerializedName("Code") val code: Double? = null,
    @SerializedName("E_Date") val date: PhpDate? = null,
    @SerializedName("Description") val description: String? = null,
    @SerializedName("Amount") val amount: Double? = null,
    @SerializedName("PaymentMode") val paymentMode: String? = null,
)

/** One saved order line from getKOT.php. */
data class KotRow(
    @SerializedName("SNo") val sNo: Double? = null,
    @SerializedName("IsPrint") val isPrint: Double? = null,
    @SerializedName("IsBill") val isBill: Double? = null,
    @SerializedName("IsCombo") val isCombo: Double? = null,
    @SerializedName("Mode") val mode: Double? = null,
    @SerializedName("S_Bill") val sBill: Double? = null,
    @SerializedName("Inv_No") val invNo: Double? = null,
    @SerializedName("Incharge") val incharge: String? = null,
    @SerializedName("cType") val cType: String? = null,
    @SerializedName("Covers") val covers: Double? = null,
    @SerializedName("ItemCode") val itemCode: Double? = null,
    @SerializedName("Description") val description: String? = null,
    @SerializedName("Ordering") val ordering: Double? = null,
    @SerializedName("CourseType") val courseType: String? = null,
    @SerializedName("Section") val section: String? = null,
    @SerializedName("Department") val department: String? = null,
    @SerializedName("PrinterName") val printerName: String? = null,
    @SerializedName("A_Price") val aPrice: Double? = null,
    @SerializedName("Price") val price: Double? = null,
    @SerializedName("Qty") val qty: Double? = null,
    @SerializedName("Value") val value: Double? = null,
)

data class LockStatus(@SerializedName("Type") val type: String? = null)

data class LockDetail(
    @SerializedName("User") val user: String? = null,
    @SerializedName("Mode") val mode: String? = null,
    @SerializedName("Name") val name: String? = null,
)

/** Body of updateKot.php - same keys the old app sent. */
data class KotRequest(
    @SerializedName("addCartModels") val lines: List<KotLineDto>,
    @SerializedName("incharge") val incharge: String,
    /** 0 = KOT button (save + print in kitchen), 1 = INVOICE button (save, then makeBill). */
    @SerializedName("is_kot") val isKot: Int,
)

data class KotLineDto(
    @SerializedName("ItemCode") val itemCode: Int,
    @SerializedName("Description") val description: String,
    @SerializedName("A_Price") val aPrice: Double,
    @SerializedName("Price") val price: Double,
    @SerializedName("Qty") val qty: Int,
    @SerializedName("Value") val value: Double,
    @SerializedName("IsPrint") val isPrint: Int,
    @SerializedName("IsBill") val isBill: Int,
    @SerializedName("IsCombo") val isCombo: Int,
    @SerializedName("Mode") val mode: Int,
    @SerializedName("S_Bill") val sBill: Int,
    @SerializedName("Inv_No") val invNo: Int,
    @SerializedName("CourseType") val courseType: String,
    @SerializedName("Ordering") val ordering: Int,
    @SerializedName("Section") val section: String,
    @SerializedName("Department") val department: String,
    @SerializedName("PrinterName") val printerName: String,
    @SerializedName("cType") val cType: String,
    @SerializedName("covers") val covers: String,
    @SerializedName("room") val room: String,
    @SerializedName("tableNo") val tableNo: String,
)
