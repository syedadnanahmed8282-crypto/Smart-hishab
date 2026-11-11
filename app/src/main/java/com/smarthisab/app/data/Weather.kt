package com.smarthisab.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
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
import java.util.Locale
import kotlin.coroutines.resume

@Serializable
data class WxDay(val date: String = "", val maxC: Double = 0.0, val minC: Double = 0.0, val rain: Int = 0, val code: Int = 1000)

@Serializable
data class Wx(
    val tempC: Double, val feelsC: Double, val text: String, val code: Int, val isDay: Boolean, val at: Long,
    val humidity: Int? = null, val windKph: Double? = null, val windDir: String = "", val pressure: Double? = null, val uv: Double? = null,
    val aqi: Int = 0, val rainChance: Int? = null, val maxC: Double? = null, val minC: Double? = null,
    val sunriseMin: Int? = null, val sunsetMin: Int? = null, val nowMin: Int? = null,
    val place: String = "", val locSrc: String = "", val days: List<WxDay> = emptyList(),
)

@Serializable private data class FcResp(val location: FcLoc = FcLoc(), val current: FcCur, val forecast: FcFc = FcFc())
@Serializable private data class FcLoc(val name: String = "", val localtime: String = "")
@Serializable private data class FcCond(val text: String = "", val code: Int = 1000)
@Serializable private data class FcAq(@SerialName("us-epa-index") val epa: Int = 0)
@Serializable private data class FcCur(
    @SerialName("temp_c") val tempC: Double,
    @SerialName("feelslike_c") val feelsC: Double,
    @SerialName("is_day") val isDay: Int = 1,
    val condition: FcCond = FcCond(),
    val humidity: Int? = null,
    @SerialName("wind_kph") val windKph: Double? = null,
    @SerialName("wind_dir") val windDir: String = "",
    @SerialName("pressure_mb") val pressure: Double? = null,
    val uv: Double? = null,
    @SerialName("air_quality") val aq: FcAq? = null,
)
@Serializable private data class FcFc(val forecastday: List<FcDay> = emptyList())
@Serializable private data class FcDay(val date: String = "", val day: FcDayInfo = FcDayInfo(), val astro: FcAstro = FcAstro())
@Serializable private data class FcDayInfo(
    @SerialName("maxtemp_c") val maxC: Double = 0.0,
    @SerialName("mintemp_c") val minC: Double = 0.0,
    @SerialName("daily_chance_of_rain") val rain: Int = 0,
    val condition: FcCond = FcCond(),
)
@Serializable private data class FcAstro(val sunrise: String = "", val sunset: String = "")

data class Loc(val lat: Double, val lon: Double, val src: String)

/** WeatherAPI.com থেকে আবহাওয়া ও ৩ দিনের পূর্বাভাস। ফোনের সঠিক লোকেশন নেয়; অনুমতি না থাকলে আনুমানিক (IP) লোকেশন। */
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

    /** force = সঙ্গে সঙ্গে নতুন করে আনে; live = GPS দিয়ে এখনকার সঠিক লোকেশন ধরে আনে */
    suspend fun refresh(force: Boolean, live: Boolean = false) {
        if (!hasKey || loading.value) return
        if (!force && state.value != null && System.currentTimeMillis() - lastFetch < 10 * 60_000L) return
        loading.value = true
        try {
            val loc = currentLocation(ctx, live)
            val q = if (loc != null) "${loc.lat},${loc.lon}" else "auto:ip"
            val (wx, apiPlace) = fetch(q)
            val place = if (loc != null) placeName(loc.lat, loc.lon, apiPlace) else apiPlace
            val full = wx.copy(place = place, locSrc = loc?.src ?: "ip")
            state.value = full
            lastFetch = full.at
            repo.setKv("wx", json.encodeToString(full))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("Weather", e.message ?: "failed")
        } finally {
            loading.value = false
        }
    }

    private suspend fun fetch(q: String): Pair<Wx, String> = withContext(Dispatchers.IO) {
        val url = URL("https://api.weatherapi.com/v1/forecast.json?key=${BuildConfig.WEATHER_API_KEY}&q=${URLEncoder.encode(q, "UTF-8")}&days=3&aqi=yes&alerts=no&lang=bn")
        val c = url.openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        try {
            if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
            val r = json.decodeFromString<FcResp>(c.inputStream.bufferedReader().use { it.readText() })
            val cur = r.current
            val d0 = r.forecast.forecastday.firstOrNull()
            val wx = Wx(
                cur.tempC, cur.feelsC, cur.condition.text, cur.condition.code, cur.isDay == 1, System.currentTimeMillis(),
                cur.humidity, cur.windKph, cur.windDir, cur.pressure, cur.uv, cur.aq?.epa ?: 0,
                d0?.day?.rain, d0?.day?.maxC, d0?.day?.minC,
                d0?.astro?.sunrise?.let { minutes(it) }, d0?.astro?.sunset?.let { minutes(it) }, minutes(r.location.localtime),
                days = r.forecast.forecastday.map { WxDay(it.date, it.day.maxC, it.day.minC, it.day.rain, it.day.condition.code) },
            )
            wx to r.location.name
        } finally {
            c.disconnect()
        }
    }

    private fun minutes(s: String): Int? {
        val m = Regex("""(\d{1,2}):(\d{2})\s*([AaPp][Mm])?""").find(s) ?: return null
        var h = m.groupValues[1].toInt()
        val mi = m.groupValues[2].toInt()
        val ap = m.groupValues[3].uppercase()
        if (ap == "PM" && h < 12) h += 12
        if (ap == "AM" && h == 12) h = 0
        return h * 60 + mi
    }

    /** বাংলায় এলাকার নাম (পেলে), নইলে WeatherAPI-র নাম */
    private suspend fun placeName(lat: Double, lon: Double, fallback: String): String = withContext(Dispatchers.IO) {
        runCatching {
            @Suppress("DEPRECATION")
            val a = Geocoder(ctx, Locale("bn", "BD")).getFromLocation(lat, lon, 1)?.firstOrNull()
            a?.subLocality ?: a?.locality ?: a?.subAdminArea
        }.getOrNull() ?: fallback
    }
}

private fun granted(ctx: Context, p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

/** ফোনের এখনকার লোকেশন (GPS, না পেলে নেটওয়ার্ক, শেষে সর্বশেষ জানা)। অনুমতি না থাকলে null। */
suspend fun currentLocation(ctx: Context, live: Boolean = false): Loc? {
    val fine = granted(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
    val coarse = granted(ctx, Manifest.permission.ACCESS_COARSE_LOCATION)
    if (!fine && !coarse) return null
    val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val providers = buildList {
        if (fine) add(LocationManager.GPS_PROVIDER)
        add(LocationManager.NETWORK_PROVIDER)
    }.filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
    for (p in providers) {
        val loc = withTimeoutOrNull(if (live) 12000 else 6000) { getCurrent(lm, p, ctx) }
        if (loc != null) return Loc(loc.latitude, loc.longitude, if (p == LocationManager.GPS_PROVIDER) "gps" else "net")
    }
    for (p in providers) {
        val loc = try { lm.getLastKnownLocation(p) } catch (e: SecurityException) { null }
        if (loc != null) return Loc(loc.latitude, loc.longitude, if (p == LocationManager.GPS_PROVIDER) "gps" else "net")
    }
    return null
}

private suspend fun getCurrent(lm: LocationManager, provider: String, ctx: Context): android.location.Location? = suspendCancellableCoroutine { cont ->
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
