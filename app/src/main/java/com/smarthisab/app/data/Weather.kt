package com.smarthisab.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import com.smarthisab.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.coroutines.resume

@Serializable
data class Wx(val tempC: Double, val feelsC: Double, val text: String, val code: Int, val isDay: Boolean, val at: Long)

@Serializable private data class WxResp(val current: WxCur)
@Serializable private data class WxCur(
    @SerialName("temp_c") val tempC: Double,
    @SerialName("feelslike_c") val feelsC: Double,
    @SerialName("is_day") val isDay: Int = 1,
    val condition: WxCond = WxCond(),
)
@Serializable private data class WxCond(val text: String = "", val code: Int = 1000)

/** WeatherAPI.com থেকে বর্তমান আবহাওয়া। ফোনের সঠিক লোকেশন নেয়; অনুমতি না থাকলে আনুমানিক (IP) লোকেশন। */
class WeatherService(private val ctx: Context, private val repo: Repo) {
    val hasKey: Boolean get() = BuildConfig.WEATHER_API_KEY.isNotBlank()
    val state = MutableStateFlow<Wx?>(null)
    val loading = MutableStateFlow(false)
    private val json = Json { ignoreUnknownKeys = true }
    private var lastFetch = 0L

    suspend fun loadCache() {
        val cached = repo.getKv("wx")?.let { runCatching { json.decodeFromString<Wx>(it) }.getOrNull() } ?: return
        if (state.value == null) { state.value = cached; lastFetch = cached.at }
    }

    /** force = true হলে (যেমন তাপমাত্রায় চাপলে) সঙ্গে সঙ্গে নতুন করে আনে; নইলে ১০ মিনিটের মধ্যে আবার আনে না */
    suspend fun refresh(force: Boolean) {
        if (!hasKey || loading.value) return
        if (!force && state.value != null && System.currentTimeMillis() - lastFetch < 10 * 60_000L) return
        loading.value = true
        try {
            val loc = currentLocation(ctx)
            val q = if (loc != null) "${loc.latitude},${loc.longitude}" else "auto:ip"
            val wx = fetch(q)
            state.value = wx
            lastFetch = wx.at
            repo.setKv("wx", json.encodeToString(wx))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("Weather", e.message ?: "failed")
        } finally {
            loading.value = false
        }
    }

    private suspend fun fetch(q: String): Wx = withContext(Dispatchers.IO) {
        val url = URL("https://api.weatherapi.com/v1/current.json?key=${BuildConfig.WEATHER_API_KEY}&q=${URLEncoder.encode(q, "UTF-8")}&lang=bn&aqi=no")
        val c = url.openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        try {
            if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
            val body = c.inputStream.bufferedReader().use { it.readText() }
            val r = json.decodeFromString<WxResp>(body).current
            Wx(r.tempC, r.feelsC, r.condition.text, r.condition.code, r.isDay == 1, System.currentTimeMillis())
        } finally {
            c.disconnect()
        }
    }
}

private fun granted(ctx: Context, p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

/** ফোনের এখনকার লোকেশন (GPS, না পেলে নেটওয়ার্ক, শেষে সর্বশেষ জানা)। অনুমতি না থাকলে null। */
suspend fun currentLocation(ctx: Context): Location? {
    val fine = granted(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
    val coarse = granted(ctx, Manifest.permission.ACCESS_COARSE_LOCATION)
    if (!fine && !coarse) return null
    val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val providers = buildList {
        if (fine) add(LocationManager.GPS_PROVIDER)
        add(LocationManager.NETWORK_PROVIDER)
    }.filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
    for (p in providers) {
        val loc = withTimeoutOrNull(6000) { getCurrent(lm, p, ctx) }
        if (loc != null) return loc
    }
    return providers.firstNotNullOfOrNull { p -> try { lm.getLastKnownLocation(p) } catch (e: SecurityException) { null } }
}

private suspend fun getCurrent(lm: LocationManager, provider: String, ctx: Context): Location? = suspendCancellableCoroutine { cont ->
    val cs = CancellationSignal()
    cont.invokeOnCancellation { cs.cancel() }
    try {
        LocationManagerCompat.getCurrentLocation(lm, provider, cs, ContextCompat.getMainExecutor(ctx)) { loc ->
            if (cont.isActive) cont.resume(loc)
        }
    } catch (e: SecurityException) {
        if (cont.isActive) cont.resume(null)
    }
}
