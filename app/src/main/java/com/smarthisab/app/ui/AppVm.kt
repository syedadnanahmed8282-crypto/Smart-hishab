package com.smarthisab.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smarthisab.app.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

data class UiMsg(val text: String, val action: String? = null, val ms: Long = 4000, val onAction: (() -> Unit)? = null)

class AppVm(val repo: Repo, val wx: WeatherService) : ViewModel() {
    private val eager = SharingStarted.Eagerly
    val folders = repo.folders.stateIn(viewModelScope, eager, emptyList())
    val entries = repo.entries.stateIn(viewModelScope, eager, emptyList())
    private val currentId = repo.kv("folder").stateIn(viewModelScope, eager, null)
    val viewer = repo.kv("viewer").map { it == "1" }.stateIn(viewModelScope, eager, false)
    // null = ফোন থেকে এখনও পড়া হয়নি
    val guest = repo.kv("guest").map<String?, Boolean?> { it == "1" }.stateIn(viewModelScope, eager, null)
    val signedLocal = repo.kv("signed").map<String?, Boolean?> { it == "1" }.stateIn(viewModelScope, eager, null)
    val lastSync = repo.kv("last_sync").stateIn(viewModelScope, eager, null)
    val auth = repo.authState.stateIn(viewModelScope, eager, AuthState.Loading)

    val current = combine(folders, currentId) { fs, id ->
        fs.firstOrNull { it.id == id } ?: fs.firstOrNull { !it.closed } ?: fs.firstOrNull()
    }.stateIn(viewModelScope, eager, null)

    val currentEntries = combine(entries, current) { es, f ->
        if (f == null) emptyList() else es.filter { it.folderId == f.id }
    }.stateIn(viewModelScope, eager, emptyList())

    val messages = MutableSharedFlow<UiMsg>(extraBufferCapacity = 16)
    val syncing = MutableStateFlow(false)
    private var realtime: Job? = null

    // ---- আবহাওয়া ----
    val weather = wx.state
    val weatherLoading = wx.loading
    init { viewModelScope.launch { wx.loadCache() } }
    fun refreshWeather(force: Boolean = false, live: Boolean = false) { viewModelScope.launch { wx.refresh(force, live) } }

    init {
        viewModelScope.launch {
            auth.collect {
                realtime?.cancel()
                if (it == AuthState.In) {
                    repo.setKv("signed", "1")
                    sync()
                    realtime = repo.startRealtime(viewModelScope)
                }
            }
        }
    }

    fun say(text: String, action: String? = null, ms: Long = 4000, onAction: (() -> Unit)? = null) {
        messages.tryEmit(UiMsg(text, action, ms, onAction))
    }

    fun act(ok: String? = null, done: () -> Unit = {}, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                ok?.let { say(it) }
                done()
                sync(false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: AppError) {
                say(e.message ?: "কিছু একটা ভুল হয়েছে")
            } catch (e: Exception) {
                say("সমস্যা হয়েছে: ${e.message ?: "অজানা"}")
            }
        }
    }

    fun sync(manual: Boolean = false) {
        if (auth.value != AuthState.In) {
            if (manual) say("সিঙ্কের জন্য লগইন করুন")
            return
        }
        viewModelScope.launch {
            syncing.value = true
            val r = repo.sync()
            syncing.value = false
            r.onSuccess { lost ->
                if (lost > 0) say("${com.smarthisab.app.domain.Fmt.bn(lost)}টা বদল অন্য ফোনের নতুন তথ্যের সাথে মেলেনি, সার্ভারের তথ্য রাখা হয়েছে", ms = 7000)
                else if (manual) say("সিঙ্ক হয়েছে")
            }
            r.onFailure { if (manual) say("সিঙ্ক হয়নি: ${it.message ?: ""}") }
        }
    }

    // ---- ফোল্ডার ----
    fun select(id: String) = act { repo.selectFolder(id) }
    fun createFolder(name: String, start: LocalDate, end: LocalDate?, goal: Double?, limit: Double?, wage: Double, target: Int?, done: () -> Unit) =
        act(done = done) { repo.createFolder(name, start, end, goal, limit, wage, target) }
    fun updateFolder(f: FolderE, ok: String? = null) = act(ok) { repo.updateFolder(f) }
    fun closeFolder(id: String, end: LocalDate?) = act("চক্র শেষ হয়েছে") { repo.closeFolder(id, end) }
    fun reopenFolder(id: String) = act("ফোল্ডার আবার চালু হয়েছে") { repo.reopenFolder(id) }
    fun deleteFolder(id: String, done: () -> Unit = {}) = act("ফোল্ডার মুছে গেছে", done) { repo.deleteFolder(id) }
    fun deleteAll() = act("সব হিসাব মুছে গেছে") { repo.deleteAllData() }

    // ---- এন্ট্রি ----
    fun saveEntry(id: String?, day: LocalDate, qty: Int, exp: Double, note: String?, done: () -> Unit) {
        val f = current.value ?: return
        act(if (id == null) "হিসাব সংরক্ষিত হয়েছে" else "সংশোধন সংরক্ষিত হয়েছে", done) { repo.saveEntry(f.id, id, day, qty, exp, note) }
    }

    fun deleteEntry(e: EntryE, done: () -> Unit = {}) {
        act(done = done) {
            if (repo.deleteEntry(e.id)) {
                val label = com.smarthisab.app.domain.Fmt.date(LocalDate.ofEpochDay(e.day))
                say("$label এর হিসাব মুছে গেছে", "ফেরান", 15000) { act("হিসাব ফিরে এসেছে") { repo.restoreEntry(e.id) } }
            }
        }
    }

    // ---- ভিউয়ার মোড ----
    fun enableViewer(pin: String, done: () -> Unit) = act(done = done) {
        val pending = repo.enableViewer(pin)
        say(if (pending > 0) "ভিউয়ার মোড চালু। ${com.smarthisab.app.domain.Fmt.bn(pending)}টা বদল আপলোড বাকি, মোড বন্ধ করলে যাবে" else "ভিউয়ার মোড চালু হয়েছে", ms = 6000)
    }
    fun disableViewer(pin: String, done: () -> Unit) = act("ভিউয়ার মোড বন্ধ হয়েছে", done) { repo.disableViewer(pin) }
    fun disableViewerPw(pw: String, done: () -> Unit) = act("ভিউয়ার মোড বন্ধ হয়েছে", done) { repo.disableViewerWithPassword(pw) }

    // ---- অ্যাকাউন্ট ----
    fun login(e: String, p: String) = act { repo.login(e.trim(), p) }
    fun google() = act { repo.loginGoogle() }
    fun register(name: String, e: String, p: String, done: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                done(repo.register(name.trim(), e.trim(), p))
            } catch (x: CancellationException) {
                throw x
            } catch (x: Exception) {
                say("রেজিস্ট্রেশন হয়নি: ${x.message ?: ""}")
            }
        }
    }
    fun resetPassword(e: String, done: () -> Unit) = act("রিসেট লিংক পাঠানো হয়েছে", done) { repo.resetPassword(e.trim()) }
    fun continueAsGuest() = act { repo.setKv("guest", "1") }
    fun fullPull() { viewModelScope.launch { repo.removeKv("since"); sync(true) } }
    fun logout() = act("লগআউট হয়েছে") { repo.signOut() }
}
