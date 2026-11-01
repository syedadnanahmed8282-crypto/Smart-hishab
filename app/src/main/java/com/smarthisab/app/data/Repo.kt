package com.smarthisab.app.data

import android.util.Log
import com.smarthisab.app.domain.Fmt
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.security.MessageDigest
import java.time.LocalDate
import java.util.UUID

class AppError(message: String) : Exception(message)
enum class AuthState { Loading, In, Out }

class Repo(db: AppDb, val supabase: SupabaseClient) {
    private val fd = db.folders()
    private val ed = db.entries()
    private val kd = db.kv()
    private val mutex = Mutex()
    private fun now() = System.currentTimeMillis()

    val folders: Flow<List<FolderE>> = fd.observe()
    val entries: Flow<List<EntryE>> = ed.observe()
    fun kv(k: String): Flow<String?> = kd.observe(k)
    suspend fun getKv(k: String): String? = kd.get(k)
    suspend fun setKv(k: String, v: String) = kd.put(KvE(k, v))
    suspend fun removeKv(k: String) = kd.remove(k)

    // ---------- লগইন ----------
    val authState: Flow<AuthState> = supabase.auth.sessionStatus.map {
        when (it) {
            is SessionStatus.Authenticated -> AuthState.In
            is SessionStatus.Initializing -> AuthState.Loading
            // নেট না থাকলে সেশন নতুন করা যায় না, কিন্তু ফোনে সেশন থাকলে লগইন করা ধরা হয়
            else -> if (supabase.auth.currentSessionOrNull() != null) AuthState.In else AuthState.Out
        }
    }
    fun email(): String? = supabase.auth.currentUserOrNull()?.email
    fun userName(): String? = supabase.auth.currentUserOrNull()?.userMetadata?.get("name")?.jsonPrimitive?.contentOrNull
    private fun signedIn() = supabase.auth.currentSessionOrNull() != null

    suspend fun login(em: String, pw: String) {
        supabase.auth.signInWith(Email) { email = em; password = pw }
        kd.remove("guest")
    }

    /** true মানে ইমেইল যাচাই বাকি */
    suspend fun register(name: String, em: String, pw: String): Boolean {
        supabase.auth.signUpWith(Email) {
            email = em; password = pw
            data = buildJsonObject { put("name", name) }
        }
        kd.remove("guest")
        return supabase.auth.currentSessionOrNull() == null
    }

    suspend fun resetPassword(em: String) { supabase.auth.resetPasswordForEmail(em, redirectUrl = "smarthisab://auth") }
    suspend fun loginGoogle() { supabase.auth.signInWith(Google) }

    suspend fun signOut() {
        requireEditable()
        if (signedIn()) {
            if (sync().isFailure) throw AppError("সিঙ্ক শেষ হয়নি, ইন্টারনেট দেখে আবার চেষ্টা করুন")
            supabase.auth.signOut()
            ed.clear(); fd.clear(); kd.clear()
        } else { kd.remove("guest"); kd.remove("signed") }
    }

    // ---------- নিয়ম ----------
    private suspend fun requireEditable() { if (kd.get("viewer") == "1") throw AppError("ভিউয়ার মোড চালু আছে, কিছু বদলানো যাবে না") }
    private suspend fun requireOpen(f: FolderE?) {
        requireEditable()
        if (f?.closed == true) throw AppError("ফোল্ডার সমাপ্ত, আগে আবার চালু করুন")
    }

    // ---------- ভিউয়ার মোড ----------
    private fun hash(pin: String) = MessageDigest.getInstance("SHA-256").digest(("smarthisab:$pin").toByteArray()).joinToString("") { "%02x".format(it) }
    suspend fun enableViewer(pin: String) {
        val p = Fmt.ascii(pin)
        if (p.length != 4 || !p.all { it.isDigit() }) throw AppError("৪ সংখ্যার PIN দিন")
        kd.put(KvE("pin", hash(p))); kd.put(KvE("viewer", "1"))
    }
    suspend fun disableViewer(pin: String) {
        if (kd.get("pin") != hash(Fmt.ascii(pin))) throw AppError("PIN ঠিক নয়")
        kd.remove("viewer")
    }
    suspend fun disableViewerWithPassword(pw: String) {
        val em = email() ?: throw AppError("পাসওয়ার্ড দিয়ে বন্ধ করতে লগইন লাগবে")
        supabase.auth.signInWith(Email) { email = em; password = pw }
        kd.remove("viewer")
    }

    // ---------- ফোল্ডার (কাজের চক্র) ----------
    suspend fun createFolder(name: String, start: LocalDate, expectedEnd: LocalDate?, goal: Double?, limit: Double?, wage: Double, target: Int?): String {
        requireEditable()
        if (name.isBlank()) throw AppError("ফোল্ডারের নাম দিন")
        val id = UUID.randomUUID().toString()
        fd.upsert(FolderE(id, name.trim(), start.toEpochDay(), expectedEnd?.toEpochDay(), null, false, goal, limit, wage, target))
        kd.put(KvE("folder", id))
        return id
    }
    suspend fun updateFolder(f: FolderE) {
        requireEditable()
        val cur = fd.get(f.id) ?: return
        if (cur.closed && (cur.startDay != f.startDay || cur.endDay != f.endDay)) throw AppError("সমাপ্ত ফোল্ডারের তারিখ বদলানো যাবে না")
        fd.upsert(f.copy(updatedAt = now(), dirty = true))
    }
    suspend fun selectFolder(id: String) = kd.put(KvE("folder", id))
    suspend fun closeFolder(id: String, end: LocalDate?) {
        requireEditable()
        val f = fd.get(id) ?: return
        if (f.closed) return
        val last = ed.ofFolder(id).maxOfOrNull { it.day }
        val endDay = (end?.toEpochDay() ?: last ?: LocalDate.now().toEpochDay()).coerceAtLeast(f.startDay)
        fd.upsert(f.copy(closed = true, endDay = endDay, updatedAt = now(), dirty = true))
    }
    suspend fun reopenFolder(id: String) {
        requireEditable()
        val f = fd.get(id) ?: return
        fd.upsert(f.copy(closed = false, endDay = null, updatedAt = now(), dirty = true))
    }
    suspend fun deleteFolder(id: String) {
        requireEditable()
        val f = fd.get(id) ?: return
        val ts = now()
        fd.upsert(f.copy(deletedAt = ts, updatedAt = ts, dirty = true))
        ed.ofFolder(id).forEach { ed.upsert(it.copy(deletedAt = ts, updatedAt = ts, dirty = true)) }
        if (kd.get("folder") == id) kd.remove("folder")
    }
    suspend fun deleteAllData() {
        requireEditable()
        val ts = now()
        fd.all().filter { it.deletedAt == null }.forEach { fd.upsert(it.copy(deletedAt = ts, updatedAt = ts, dirty = true)) }
        ed.all().filter { it.deletedAt == null }.forEach { ed.upsert(it.copy(deletedAt = ts, updatedAt = ts, dirty = true)) }
        kd.remove("folder")
    }

    // ---------- দৈনিক এন্ট্রি ----------
    suspend fun saveEntry(folderId: String, id: String?, day: LocalDate, qty: Int, expense: Double, note: String?) {
        val f = fd.get(folderId) ?: throw AppError("ফোল্ডার পাওয়া যায়নি")
        requireOpen(f)
        // যেকোনো তারিখ দেওয়া যায়; আগের তারিখ হলে ফোল্ডারের শুরুর তারিখ সেই দিনে সরে আসে
        if (day.toEpochDay() < f.startDay) fd.upsert(f.copy(startDay = day.toEpochDay(), updatedAt = now(), dirty = true))
        if (qty < 0 || expense < 0) throw AppError("সংখ্যা ঋণাত্মক হতে পারে না")
        val dup = ed.byDay(folderId, day.toEpochDay())
        if (dup != null && dup.id != id) throw AppError("এই তারিখে আগেই এন্ট্রি আছে, সেটা সম্পাদনা করুন")
        val old = id?.let { ed.get(it) }
        val n = note?.trim()?.takeIf { it.isNotEmpty() }
        ed.upsert(EntryE(id ?: UUID.randomUUID().toString(), folderId, day.toEpochDay(), qty, expense, old?.rate ?: f.wageRate, n, null, now(), true))
    }
    suspend fun deleteEntry(id: String): Boolean {
        val e = ed.get(id) ?: return false
        requireOpen(fd.get(e.folderId))
        ed.upsert(e.copy(deletedAt = now(), updatedAt = now(), dirty = true))
        return true
    }
    suspend fun restoreEntry(id: String) {
        val e = ed.get(id) ?: return
        requireOpen(fd.get(e.folderId))
        val dup = ed.byDay(e.folderId, e.day)
        if (dup != null && dup.id != id) throw AppError("ওই তারিখে নতুন এন্ট্রি আছে, ফেরানো গেল না")
        ed.upsert(e.copy(deletedAt = null, updatedAt = now(), dirty = true))
    }

    // ---------- সিঙ্ক ----------
    suspend fun sync(): Result<Unit> = mutex.withLock {
        try {
            val uid = supabase.auth.currentUserOrNull()?.id ?: throw AppError("লগইন নেই")
            push(uid)
            pull()
            snapshots()
            kd.put(KvE("last_sync", now().toString()))
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("Sync", e)
            Result.failure(e)
        }
    }

    private suspend fun push(uid: String) {
        val fs = fd.dirty()
        val es = ed.dirty()
        // সমাপ্ত ফোল্ডারে নতুন এন্ট্রি থাকলে আগে চালু অবস্থায় পাঠাই, এন্ট্রি যাওয়ার পর সমাপ্ত করি
        val closing = fs.filter { f -> f.closed && es.any { it.folderId == f.id } }
        val closingIds = closing.map { it.id }.toSet()
        if (fs.isNotEmpty()) supabase.from("folders").upsert(fs.map { it.toUp(uid, forceRunning = it.id in closingIds) })
        if (es.isNotEmpty()) supabase.from("entries").upsert(es.map { it.toUp(uid) })
        if (closing.isNotEmpty()) supabase.from("folders").upsert(closing.map { it.toUp(uid) })
        for (f in fs) fd.get(f.id)?.let { if (it.updatedAt == f.updatedAt) fd.upsert(it.copy(dirty = false)) }
        for (e in es) ed.get(e.id)?.let { if (it.updatedAt == e.updatedAt) ed.upsert(it.copy(dirty = false)) }
    }

    private suspend fun pull() {
        val since = kd.get("since") ?: "1970-01-01T00:00:00Z"
        val rf = supabase.from("folders").select {
            filter { gt("updated_at", since) }
            order("updated_at", Order.ASCENDING)
            limit(1000)
        }.decodeList<FolderDown>()
        val re = supabase.from("entries").select {
            filter { gt("updated_at", since) }
            order("updated_at", Order.ASCENDING)
            limit(1000)
        }.decodeList<EntryDown>()
        var maxMs = parseTs(since)
        var maxTs = since
        for (r in rf) {
            val ms = parseTs(r.updatedAt)
            val cur = fd.get(r.id)
            if (cur != null && cur.dirty && cur.updatedAt > ms) continue
            fd.upsert(r.toLocal())
            if (ms > maxMs) { maxMs = ms; maxTs = r.updatedAt.replace("+00:00", "Z") }
        }
        for (r in re) {
            val ms = parseTs(r.updatedAt)
            val cur = ed.get(r.id)
            if (cur != null && cur.dirty && cur.updatedAt > ms) continue
            ed.upsert(r.toLocal())
            if (ms > maxMs) { maxMs = ms; maxTs = r.updatedAt.replace("+00:00", "Z") }
        }
        if (maxTs != since) kd.put(KvE("since", maxTs))
    }

    /** সম্পূর্ণ হওয়া সপ্তাহের রিপোর্ট সার্ভারে সংরক্ষণ (ব্যর্থ হলে সমস্যা নেই) */
    private suspend fun snapshots() {
        for (f in fd.all().filter { it.deletedAt == null && !it.dirty }) {
            try {
                supabase.postgrest.rpc("snapshot_weekly_reports", buildJsonObject { put("p_folder", f.id) })
            } catch (e: CancellationException) { throw e } catch (e: Exception) { Log.w("Sync", "snapshot: ${e.message}") }
        }
    }

    @OptIn(FlowPreview::class)
    fun startRealtime(scope: CoroutineScope): Job = scope.launch {
        try {
            val ch = supabase.channel("db-changes")
            val a = ch.postgresChangeFlow<PostgresAction>(schema = "public") { table = "entries" }
            val b = ch.postgresChangeFlow<PostgresAction>(schema = "public") { table = "folders" }
            ch.subscribe(blockUntilSubscribed = true)
            merge(a, b).debounce(800).collect { sync() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("Realtime", e)
        }
    }
}
