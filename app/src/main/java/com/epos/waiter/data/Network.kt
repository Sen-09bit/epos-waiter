package com.epos.waiter.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import java.net.Inet4Address
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/** The same pages the old ePOS app called. Served by ePOS Mobile Server at http://<pc>/epos/ */
interface EposApi {
    @GET("getUsers.php") suspend fun getUsers(): ServerResponse
    @GET("getItems.php") suspend fun getMasterData(): ServerResponse
    @GET("getRoom.php") suspend fun getRooms(): ServerResponse
    @GET("getComments.php") suspend fun getComments(): ServerResponse
    @GET("getConcerns.php") suspend fun getConcerns(): ServerResponse
    @GET("getExpense.php") suspend fun getExpenses(): ServerResponse

    @FormUrlEncoded @POST("insert_expense.php")
    suspend fun insertExpense(@Field("description") description: String, @Field("amount") amount: String): ServerResponse

    @FormUrlEncoded @POST("getTables.php")
    suspend fun getTables(@Field("room_no") room: String): ServerResponse

    /** Same as getTables.php plus covers, amount, minutes since last KOT, floor position and lock. */
    @FormUrlEncoded @POST("getTablesPlan.php")
    suspend fun getTablesPlan(@Field("room_no") room: String): ServerResponse

    @FormUrlEncoded @POST("getKOT.php")
    suspend fun getKot(@Field("room_no") room: String, @Field("table_no") table: String): ServerResponse

    @FormUrlEncoded @POST("checkTableStatus.php")
    suspend fun checkTable(@Field("room") room: String, @Field("table") table: String): ServerResponse

    /** Locks ("Insert") or unlocks ("Delete") a table in Table_Log. */
    @FormUrlEncoded @POST("insertData.php")
    suspend fun tableLock(
        @Field("type") type: String,
        @Field("room") room: String,
        @Field("id") table: String,
        @Field("name") user: String,
        @Field("mobile") kind: String,
        @Field("lastname") device: String,
    ): ServerResponse

    @POST("updateKot.php") suspend fun saveOrder(@Body body: KotRequest): ServerResponse

    @FormUrlEncoded @POST("makeBill.php")
    suspend fun makeBill(@Field("room") room: String, @Field("table") table: String): ServerResponse

    @FormUrlEncoded @POST("getChangeTableList.php")
    suspend fun getChangeTableList(@Field("room") room: String): ServerResponse

    @FormUrlEncoded @POST("updateChangeTable.php")
    suspend fun changeTable(
        @Field("user") user: String, @Field("room") room: String,
        @Field("source") source: String, @Field("destination") destination: String,
    ): ServerResponse

    @FormUrlEncoded @POST("getJoinTableList.php")
    suspend fun getJoinTableList(@Field("room") room: String): ServerResponse

    /** destination = tables separated by commas. */
    @FormUrlEncoded @POST("updateJoinTable.php")
    suspend fun joinTables(
        @Field("room") room: String, @Field("source") source: String, @Field("destination") destination: String,
    ): ServerResponse

    @FormUrlEncoded @POST("updateConcerns.php")
    suspend fun updateProfile(
        @Field("ConCode") conCode: String, @Field("ConcernName") name: String, @Field("SubTitle") subTitle: String,
        @Field("Add1") add1: String, @Field("Add2") add2: String, @Field("City") city: String,
        @Field("ZipCode") zip: String, @Field("Country") country: String, @Field("Phone") phone: String,
        @Field("E_mail") email: String, @Field("Website") website: String, @Field("VATNo") vatNo: String,
        @Field("MobileNo") mobile: String,
    ): ServerResponse

    @FormUrlEncoded @POST("changePassword.php")
    suspend fun changePassword(@Field("user_id") userId: String, @Field("password") password: String): ServerResponse
}

object ServerClient {
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS) // the server waits up to ~7 s for kitchen printers
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private var cachedUrl: String = ""
    private var cachedApi: EposApi? = null

    /** "192.168.1.20" or "192.168.1.20:8080" -> "http://192.168.1.20/epos/" */
    fun baseUrlFor(address: String): String {
        var a = address.trim().removePrefix("http://").removePrefix("https://").trimEnd('/')
        if (a.endsWith("/epos", ignoreCase = true)) a = a.dropLast(5)
        return "http://$a/epos/"
    }

    fun api(baseUrl: String): EposApi {
        cachedApi?.let { if (cachedUrl == baseUrl) return it }
        val api = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(http)
            .addConverterFactory(GsonConverterFactory.create(Gson()))
            .build()
            .create(EposApi::class.java)
        cachedUrl = baseUrl
        cachedApi = api
        return api
    }

    /** Plain words for the waiter instead of Java exception names. */
    fun friendly(e: Throwable): String = when (e) {
        is SocketTimeoutException -> "The ePOS computer did not answer in time. Try again."
        is UnknownHostException, is java.net.ConnectException, is java.net.NoRouteToHostException ->
            "Cannot reach the ePOS computer. Check the Wi-Fi and that ePOS Mobile Server is running."
        is retrofit2.HttpException -> "The ePOS computer returned an error (${e.code()})."
        is com.google.gson.JsonParseException -> "Unexpected answer from the ePOS computer."
        else -> e.message ?: e.javaClass.simpleName
    }
}

data class FoundServer(val address: String, val name: String)

/** Finds ePOS Mobile Server on the phone's Wi-Fi by asking every address in the same /24 network. */
object Discovery {
    private val probe: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(700, TimeUnit.MILLISECONDS)
        .readTimeout(1500, TimeUnit.MILLISECONDS)
        .build()

    fun localIpv4(context: Context): String? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val candidates = mutableListOf<android.net.Network>()
        cm.activeNetwork?.let { candidates.add(it) }
        @Suppress("DEPRECATION")
        cm.allNetworks.forEach { if (it !in candidates) candidates.add(it) }
        for (n in candidates) {
            val caps = cm.getNetworkCapabilities(n) ?: continue
            val local = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            if (!local) continue
            val props = cm.getLinkProperties(n) ?: continue
            val ip = props.linkAddresses.map { it.address }.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
            if (ip != null) return ip.hostAddress
        }
        return null
    }

    /** Returns the servers found (usually one). Takes about 3 seconds. */
    suspend fun scan(context: Context, port: Int = 80): List<FoundServer> = withContext(Dispatchers.IO) {
        val ip = localIpv4(context) ?: return@withContext emptyList()
        val parts = ip.split(".")
        if (parts.size != 4) return@withContext emptyList()
        val prefix = "${parts[0]}.${parts[1]}.${parts[2]}."
        val gate = Semaphore(48)
        coroutineScope {
            (1..254).map { host ->
                async {
                    gate.withPermit { check(prefix + host, port) }
                }
            }.awaitAll().filterNotNull()
        }
    }

    private fun check(host: String, port: Int): FoundServer? {
        val address = if (port == 80) host else "$host:$port"
        return try {
            val req = Request.Builder().url("http://$address/epos/").build()
            probe.newCall(req).execute().use { res ->
                val body = res.body?.string().orEmpty()
                if (!body.contains("ePOS Mobile Server")) return null
            }
            FoundServer(address, serverName(address) ?: "ePOS computer")
        } catch (e: Exception) {
            null
        }
    }

    private fun serverName(address: String): String? = try {
        val req = Request.Builder().url("http://$address/epos/hello.php").build()
        probe.newCall(req).execute().use { res ->
            if (!res.isSuccessful) return null
            Gson().fromJson(res.body?.string(), ServerResponse::class.java)?.serverName
        }
    } catch (e: Exception) {
        null
    }
}

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("epos_waiter", Context.MODE_PRIVATE)

    var serverAddress: String
        get() = sp.getString("server_address", "").orEmpty()
        set(v) = sp.edit().putString("server_address", v).apply()

    var lastUserId: Int
        get() = sp.getInt("last_user_id", 0)
        set(v) = sp.edit().putInt("last_user_id", v).apply()

    var lastRoom: String
        get() = sp.getString("last_room", "").orEmpty()
        set(v) = sp.edit().putString("last_room", v).apply()
}
