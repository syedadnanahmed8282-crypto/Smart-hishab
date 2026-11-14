package com.smarthisab.app.data

import android.content.Context
import com.russhwolf.settings.SharedPreferencesSettings
import com.smarthisab.app.BuildConfig
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.SettingsCodeVerifierCache
import io.github.jan.supabase.auth.SettingsSessionManager
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime

fun buildSupabase(context: Context? = null) = createSupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_KEY) {
    defaultSerializer = KotlinXSerializer(Json { ignoreUnknownKeys = true; encodeDefaults = true })
    install(Auth) {
        scheme = "smarthisab"
        host = "auth"
        if (context != null) {
            val sp = SharedPreferencesSettings(context.getSharedPreferences("smarthisab_auth", Context.MODE_PRIVATE))
            sessionManager = SettingsSessionManager(settings = sp)
            codeVerifierCache = SettingsCodeVerifierCache(settings = sp)
        } else {
            sessionManager = MemorySessionManager()
            codeVerifierCache = MemoryCodeVerifierCache()
        }
    }
    install(Postgrest)
    install(Realtime)
}

@Serializable
data class FolderUp(
    val id: String,
    @SerialName("user_id") val userId: String,
    val name: String,
    @SerialName("start_date") val startDate: String,
    @SerialName("expected_end_date") val expectedEndDate: String?,
    @SerialName("end_date") val endDate: String?,
    val status: String,
    @SerialName("income_goal") val incomeGoal: Double?,
    @SerialName("expense_limit") val expenseLimit: Double?,
    @SerialName("wage_rate") val wageRate: Double,
    @SerialName("daily_target_pcs") val dailyTargetPcs: Int?,
    @SerialName("deleted_at") val deletedAt: String?,
    @SerialName("base_version") val baseVersion: Int,
    val mode: String = "main",
)

@Serializable
data class FolderDown(
    val id: String,
    val name: String,
    @SerialName("start_date") val startDate: String,
    @SerialName("expected_end_date") val expectedEndDate: String? = null,
    @SerialName("end_date") val endDate: String? = null,
    val status: String,
    @SerialName("income_goal") val incomeGoal: Double? = null,
    @SerialName("expense_limit") val expenseLimit: Double? = null,
    @SerialName("wage_rate") val wageRate: Double = 50.0,
    @SerialName("daily_target_pcs") val dailyTargetPcs: Int? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("updated_at") val updatedAt: String,
    val version: Int = 1,
    val mode: String = "main",
)

@Serializable
data class EntryUp(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("folder_id") val folderId: String,
    @SerialName("entry_date") val entryDate: String,
    val quantity: Int,
    val expense: Double,
    @SerialName("rate_per_100") val ratePer100: Double,
    val note: String?,
    @SerialName("deleted_at") val deletedAt: String?,
    @SerialName("base_version") val baseVersion: Int,
    val mode: String = "main",
    val kind: String = "expense",
    val category: String? = null,
    val amount: Double = 0.0,
)

@Serializable
data class EntryDown(
    val id: String,
    @SerialName("folder_id") val folderId: String,
    @SerialName("entry_date") val entryDate: String,
    val quantity: Int,
    val expense: Double,
    @SerialName("rate_per_100") val ratePer100: Double,
    val note: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("updated_at") val updatedAt: String,
    val version: Int = 1,
    val mode: String = "main",
    val kind: String = "expense",
    val category: String? = null,
    val amount: Double = 0.0,
)

@Serializable data class PushErr(val id: String, val msg: String = "")
@Serializable data class PushRes(val ok: Int = 0, val conflicts: List<String> = emptyList(), val errors: List<PushErr> = emptyList())

private fun iso(ms: Long?): String? = ms?.let { Instant.ofEpochMilli(it).toString() }
fun parseTs(s: String): Long = OffsetDateTime.parse(s).toInstant().toEpochMilli()

fun FolderE.toUp(uid: String, forceRunning: Boolean = false, skipCheck: Boolean = false) = FolderUp(
    id, uid, name, LocalDate.ofEpochDay(startDay).toString(),
    expectedEndDay?.let { LocalDate.ofEpochDay(it).toString() },
    if (closed && !forceRunning) endDay?.let { LocalDate.ofEpochDay(it).toString() } else null,
    if (closed && !forceRunning) "closed" else "running",
    incomeGoal, expenseLimit, wageRate, dailyTarget, iso(deletedAt), if (skipCheck) 0 else version, mode,
)

fun FolderDown.toLocal() = FolderE(
    id, name, LocalDate.parse(startDate).toEpochDay(), expectedEndDate?.let { LocalDate.parse(it).toEpochDay() },
    endDate?.let { LocalDate.parse(it).toEpochDay() }, status == "closed", incomeGoal, expenseLimit, wageRate,
    dailyTargetPcs, deletedAt?.let { parseTs(it) }, parseTs(updatedAt), dirty = false, version = version, mode = mode,
)

fun EntryE.toUp(uid: String) = EntryUp(
    id, uid, folderId, LocalDate.ofEpochDay(day).toString(), quantity, expense, rate, note, iso(deletedAt), version, mode, kind, category, amount,
)

fun EntryDown.toLocal() = EntryE(
    id, folderId, LocalDate.parse(entryDate).toEpochDay(), quantity, expense, ratePer100, note,
    deletedAt?.let { parseTs(it) }, parseTs(updatedAt), dirty = false, version = version,
    mode = mode, kind = kind, category = category, amount = amount,
)
