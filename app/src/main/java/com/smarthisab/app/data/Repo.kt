package com.smarthisab.app.data

import android.util.Log
import com.smarthisab.app.domain.Cats
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
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
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

    // ---------- হিসাবের ধরন ও খাত ----------
    /** প্রতিটা ধরনের বর্তমান ফোল্ডার আলাদা মনে থাকে ("folder" = মালের হিসাব, আগের ডাটার সাথে মিল রেখে) */
    private fun folderKey(mode: String) = if (mode == "main") "folder" else "folder_$mode"
    suspend fun setMode(mode: String) = kd.put(KvE("mode", mode))

    fun customCats(mode: String, kind: String): Flow<List<String>> =
        kd.observe("cats_${mode}_$kind").map { v -> v?.split("\n")?.filter { it.isNotBlank() } ?: emptyList() }

    private fun defaultCats(kind: String) = if (kind == "income") Cats.income else Cats.expense

    suspend fun addCat(mode: String, kind: String, name: String) {
        requireEditable()
        val n = name.trim().take(40)
        if (n.isEmpty()) throw AppError("খাতের নাম দিন")
        val key = "cats_${mode}_$kind"
        val cur = kd.get(key)?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()
        if (n in defaultCats(kind) || n in cur) throw AppError("এই খাত আগেই আছে")
        kd.put(KvE(key, (cur + n).joinToString("\n")))
    }

    suspend fun removeCat(mode: String, kind: String, name: String) {
        requireEditable()
        val key = "cats_${mode}_$kind"
        val cur = kd.get(key)?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()
        kd.put(KvE(key, (cur - name).joinToString("\n")))
    }

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
    /** চালু করার আগে বাকি বদল আপলোড করে নেয়। যেগুলো তবু বাকি থাকে তার সংখ্যা ফেরত দেয়। */
    suspend fun enableViewer(pin: String): Int {
        val p = Fmt.ascii(pin)
        if (p.length != 4 || !p.all { it.isDigit() }) throw AppError("৪ সংখ্যার PIN দিন")
        if (signedIn()) sync()
        val pending = fd.dirty().size + ed.dirty().size
        kd.put(KvE("pin", hash(p))); kd.put(KvE("viewer", "1"))
        return pending
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
    suspend fun createFolder(name: String, start: LocalDate, expectedEnd: LocalDate?, goal: Double?, limit: Double?, wage: Double, target: Int?, mode: String = "main"): String {
        requireEditable()
        if (name.isBlank()) throw AppError("ফোল্ডারের নাম দিন")
        val id = UUID.randomUUID().toString()
        fd.upsert(FolderE(id, name.trim(), start.toEpochDay(), expectedEnd?.toEpochDay(), null, false, goal, limit, wage, target, mode = mode))
        kd.put(KvE(folderKey(mode), id))
        return id
    }
    suspend fun updateFolder(f: FolderE) {
        requireEditable()
        val cur = fd.get(f.id) ?: return
        if (cur.closed && (cur.startDay != f.startDay || cur.endDay != f.endDay)) throw AppError("সমাপ্ত ফোল্ডারের তারিখ বদলানো যাবে না")
        fd.upsert(f.copy(updatedAt = now(), dirty = true))
    }
    suspend fun selectFolder(id: String) { val f = fd.get(id); kd.put(KvE(folderKey(f?.mode ?: "main"), id)) }
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
        val key = folderKey(f.mode)
        if (kd.get(key) == id) kd.remove(key)
    }
    /** শুধু বর্তমান ধরনের তথ্য মোছে — অন্য ধরনের ফোল্ডার ও এন্ট্রি অক্ষত থাকে */
    suspend fun deleteAllData(mode: String) {
        requireEditable()
        val ts = now()
        fd.all().filter { it.deletedAt == null && it.mode == mode }.forEach { fd.upsert(it.copy(deletedAt = ts, updatedAt = ts, dirty = true)) }
        ed.all().filter { it.deletedAt == null && it.mode == mode }.forEach { ed.upsert(it.copy(deletedAt = ts, updatedAt = ts, dirty = true)) }
        kd.remove(folderKey(mode))
    }

    // ---------- দৈনিক এন্ট্রি ----------
    suspend fun saveEntry(folderId: String, id: String?, day: LocalDate, qty: Int, expense: Double, note: String?) {
        val f = fd.get(folderId) ?: throw AppError("ফোল্ডার পাওয়া যায়নি")
        requireOpen(f)
        if (f.mode != "main") throw AppError("এই ফোল্ডারের জন্য আয়/খরচের এন্ট্রি দিতে হবে")
        // যেকোনো তারিখ দেওয়া যায়; আগের তারিখ হলে ফোল্ডারের শুরুর তারিখ সেই দিনে সরে আসে
        if (day.toEpochDay() < f.startDay) fd.upsert(f.copy(startDay = day.toEpochDay(), updatedAt = now(), dirty = true))
        if (qty < 0 || expense < 0) throw AppError("সংখ্যা ঋণাত্মক হতে পারে না")
        val dup = ed.byDay(folderId, day.toEpochDay())
        if (dup != null && dup.id != id) throw AppError("এই তারিখে আগেই এন্ট্রি আছে, সেটা সম্পাদনা করুন")
        val old = id?.let { ed.get(it) }
        val n = note?.trim()?.takeIf { it.isNotEmpty() }
        ed.upsert(EntryE(id ?: UUID.randomUUID().toString(), folderId, day.toEpochDay(), qty, expense, old?.rate ?: f.wageRate, n, null, now(), true, old?.version ?: 0))
    }
    /** দৈনিক খরচ ও আয়-ব্যয়ের এন্ট্রি: একদিনে একাধিক এন্ট্রি চলে। দৈনিক খরচে সবসময় খরচ। */
    suspend fun saveMoneyEntry(folderId: String, id: String?, day: LocalDate, kind: String, amount: Double, category: String?, note: String?) {
        val f = fd.get(folderId) ?: throw AppError("ফোল্ডার পাওয়া যায়নি")
        requireOpen(f)
        if (f.mode == "main") throw AppError("মালের হিসাবে মাল দিয়ে এন্ট্রি দিন")
        if (amount <= 0) throw AppError("পরিমাণ দিন")
        val k = if (f.mode == "alt" || kind != "income") "expense" else "income"
        if (day.toEpochDay() < f.startDay) fd.upsert(f.copy(startDay = day.toEpochDay(), updatedAt = now(), dirty = true))
        val old = id?.let { ed.get(it) }
        val cat = category?.trim()?.takeIf { it.isNotEmpty() }
        val n = note?.trim()?.takeIf { it.isNotEmpty() }
        ed.upsert(EntryE(id ?: UUID.randomUUID().toString(), folderId, day.toEpochDay(), 0, 0.0, 0.0, n, null, now(), true, old?.version ?: 0, f.mode, k, cat, amount))
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
    /** সফল হলে ফেরত দেয়: অন্য ফোনের নতুন তথ্যের সাথে না মেলায় বাদ পড়া বদলের সংখ্যা */
    suspend fun sync(): Result<Int> = mutex.withLock {
        try {
            val uid = supabase.auth.currentUserOrNull()?.id ?: throw AppError("লগইন নেই")
            // ভিউয়ার মোডে শুধু ডাউনলোড, কিছুই আপলোড হয় না
            val lost = if (kd.get("viewer") == "1") 0 else push(uid)
            pull()
            snapshots()
            kd.put(KvE("last_sync", now().toString()))
            Result.success(lost)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("Sync", e)
            Result.failure(e)
        }
    }

    private val enc = Json { encodeDefaults = true; explicitNulls = true }
    private fun isServerWins(e: PushErr) = e.msg.contains("সমাপ্ত") || e.msg.contains("one_per_day") || e.msg.contains("duplicate key")
    private fun nextVer(v: Int, passes: Int = 1) = if (v == 0) passes else v + passes

    private suspend fun rpcPush(fn: String, rows: JsonElement): PushRes =
        supabase.postgrest.rpc(fn, buildJsonObject { put("p_rows", rows) }).decodeAs<PushRes>()

    /** আপলোড: প্রতিটা সারির সাথে "আমি সংস্করণ N দেখে বদলেছি" পাঠানো হয়। সার্ভারে সংস্করণ এগিয়ে গেলে ওই সারি নেওয়া হয় না। */
    private suspend fun push(uid: String): Int {
        val fs = fd.dirty()
        val es = ed.dirty()
        if (fs.isEmpty() && es.isEmpty()) return 0
        val closing = fs.filter { f -> f.closed && es.any { it.folderId == f.id } }
        val closingIds = closing.map { it.id }.toSet()
        var lost = 0
        var firstErr: String? = null

        // ১) ফোল্ডার (সমাপ্ত ফোল্ডারে এন্ট্রি থাকলে আগে চালু অবস্থায়)
        if (fs.isNotEmpty()) {
            val res = rpcPush("push_folders", enc.encodeToJsonElement(ListSerializer(FolderUp.serializer()), fs.map { it.toUp(uid, forceRunning = it.id in closingIds) }))
            val wins = res.conflicts.toSet() + res.errors.filter { isServerWins(it) }.map { it.id }
            val failing = wins + res.errors.map { it.id }
            res.errors.firstOrNull { !isServerWins(it) }?.let { firstErr = firstErr ?: it.msg }
            for (f in fs) if (f.id !in failing && f.id !in closingIds) fd.get(f.id)?.let { if (it.updatedAt == f.updatedAt) fd.upsert(it.copy(dirty = false, version = nextVer(f.version))) }
            lost += refetchFolders(wins)
        }
        // ২) এন্ট্রি
        if (es.isNotEmpty()) {
            val res = rpcPush("push_entries", enc.encodeToJsonElement(ListSerializer(EntryUp.serializer()), es.map { it.toUp(uid) }))
            val wins = res.conflicts.toSet() + res.errors.filter { isServerWins(it) }.map { it.id }
            val failing = wins + res.errors.map { it.id }
            res.errors.firstOrNull { !isServerWins(it) }?.let { firstErr = firstErr ?: it.msg }
            for (e in es) if (e.id !in failing) ed.get(e.id)?.let { if (it.updatedAt == e.updatedAt) ed.upsert(it.copy(dirty = false, version = nextVer(e.version))) }
            lost += refetchEntries(wins)
        }
        // ৩) এন্ট্রি যাওয়ার পর ফোল্ডার সমাপ্ত করা
        if (closing.isNotEmpty()) {
            val res = rpcPush("push_folders", enc.encodeToJsonElement(ListSerializer(FolderUp.serializer()), closing.map { it.toUp(uid, skipCheck = true) }))
            val failing = res.conflicts.toSet() + res.errors.map { it.id }
            res.errors.firstOrNull { !isServerWins(it) }?.let { firstErr = firstErr ?: it.msg }
            for (f in closing) if (f.id !in failing) fd.get(f.id)?.let { if (it.updatedAt == f.updatedAt) fd.upsert(it.copy(dirty = false, version = nextVer(f.version, 2))) }
            lost += refetchFolders(res.conflicts.toSet() + res.errors.filter { isServerWins(it) }.map { it.id })
        }
        firstErr?.let { throw AppError(it) }
        return lost
    }

    /** সংঘর্ষে সার্ভারের তথ্য রেখে ফোনের বদল বাদ */
    private suspend fun refetchFolders(ids: Set<String>): Int {
        if (ids.isEmpty()) return 0
        val remote = supabase.from("folders").select { filter { isIn("id", ids.toList()) } }.decodeList<FolderDown>()
        remote.forEach { fd.upsert(it.toLocal()) }
        return ids.size
    }

    private suspend fun refetchEntries(ids: Set<String>): Int {
        if (ids.isEmpty()) return 0
        val remote = supabase.from("entries").select { filter { isIn("id", ids.toList()) } }.decodeList<EntryDown>()
        remote.forEach { ed.upsert(it.toLocal()) }
        val found = remote.map { it.id }.toSet()
        ids.filter { it !in found }.forEach { ed.delete(it) }  // সার্ভারে নেই (যেমন একই তারিখে অন্য ফোনের এন্ট্রি আগেই আছে)
        return ids.size
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
        for (f in fd.all().filter { it.deletedAt == null && !it.dirty && it.mode == "main" }) {
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
