package com.smarthisab.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarthisab.app.data.Wx
import com.smarthisab.app.domain.Fmt
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val Ink = Color(0xFF0F172A)
private val Gray = Color(0xFF64748B)
private val Panel = Color(0xFFF5F9FC)

private fun hm(min: Int) = Fmt.bn(String.format("%02d:%02d", min / 60, min % 60))
private fun hmText(min: Int) = "${Fmt.bn(min / 60)} ঘ ${Fmt.bn(min % 60)} মি"

private fun dirBn(d: String): String = when (d.uppercase()) {
    "N", "NNE", "NNW" -> "উত্তর"; "NE", "ENE" -> "উত্তর-পূর্ব"; "E", "ESE" -> "পূর্ব"; "SE", "SSE" -> "দক্ষিণ-পূর্ব"
    "S", "SSW" -> "দক্ষিণ"; "SW", "WSW" -> "দক্ষিণ-পশ্চিম"; "W", "WNW" -> "পশ্চিম"; "NW" -> "উত্তর-পশ্চিম"; else -> ""
}
private fun dirDeg(d: String): Float = when (d.uppercase()) {
    "N", "NNE", "NNW" -> 0f; "NE", "ENE" -> 45f; "E", "ESE" -> 90f; "SE", "SSE" -> 135f
    "S", "SSW" -> 180f; "SW", "WSW" -> 225f; "W", "WNW" -> 270f; "NW" -> 315f; else -> 0f
}
private fun uvLabel(u: Double) = when { u < 3 -> "নিম্ন"; u < 6 -> "মাঝারি"; u < 8 -> "উচ্চ"; u < 11 -> "অতি উচ্চ"; else -> "চরম" }
private fun aqiLabel(i: Int) = when (i) { 1 -> "ভালো"; 2 -> "মাঝারি"; 3 -> "ঝুঁকিপূর্ণ"; 4 -> "অস্বাস্থ্যকর"; 5 -> "খুব খারাপ"; 6 -> "বিপজ্জনক"; else -> "—" }
private fun iconFor(code: Int): String = when {
    code == 1000 -> Ic.sun
    code == 1063 || code in 1150..1201 || code in 1240..1264 || code in 1273..1282 -> Ic.rain
    code in 1003..1006 -> Ic.csun
    else -> Ic.cloud
}

/** সেটিংস থেকে খোলা আবহাওয়ার বিস্তারিত: অবস্থান ও লাইভ লোকেশন, ৬টা তথ্য, সচল সূর্যোদয়-সূর্যাস্ত, ৩ দিনের পূর্বাভাস */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherSheet(wx: Wx?, loading: Boolean, hasKey: Boolean, onRefresh: () -> Unit, onLive: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
            val spin by rememberInfiniteTransition(label = "spin").animateFloat(0f, 360f, infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "s")
            val bob by rememberInfiniteTransition(label = "bob").animateFloat(0f, -3f, infiniteRepeatable(tween(1500), RepeatMode.Reverse), label = "b")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFFFEF3C7)), contentAlignment = Alignment.Center) {
                        Ico(if (wx != null) iconFor(wx.code) else Ic.csun, Modifier.graphicsLayer { translationY = bob }, size = 30.dp, tint = Amber, sw = 1.9f)
                    }
                    Text(
                        buildAnnotatedString {
                            append(if (wx == null) "--" else Fmt.bn(wx.tempC.roundToInt())); append("°")
                            withStyle(SpanStyle(fontSize = 18.sp, color = Gray, fontWeight = FontWeight.SemiBold)) { append("C") }
                        },
                        fontSize = 40.sp, lineHeight = 46.sp, fontWeight = FontWeight.Bold, color = Ink, maxLines = 1,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Box(Modifier.size(40.dp).bounceClick(true, onRefresh).clip(CircleShape).background(Sky100), contentAlignment = Alignment.Center) {
                        Ico(Ic.refresh, Modifier.graphicsLayer { rotationZ = if (loading) spin else 0f }, size = 20.dp, tint = Sky800)
                    }
                    val mins = wx?.let { ((System.currentTimeMillis() - it.at) / 60000).toInt().coerceAtLeast(0) }
                    Text(if (mins == null) "" else if (mins < 1) "এইমাত্র" else "${Fmt.bn(mins)} মিনিট আগে", fontSize = 12.sp, lineHeight = 16.sp, color = Gray)
                }
            }
            if (wx == null) {
                Text(if (!hasKey) "আবহাওয়ার কী বসানো নেই।" else if (loading) "আবহাওয়া আনা হচ্ছে…" else "আবহাওয়া পাওয়া যায়নি। ইন্টারনেট দেখে ওপরের রিফ্রেশ চাপুন।", Modifier.padding(vertical = 16.dp), color = Gray, fontSize = 14.sp)
                return@Column
            }
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(wx.text) }
                    withStyle(SpanStyle(color = Gray)) {
                        append(" · অনুভূত ${Fmt.bn(wx.feelsC.roundToInt())}°")
                        if (wx.maxC != null && wx.minC != null) append(" · সর্বোচ্চ ${Fmt.bn(wx.maxC.roundToInt())}° / সর্বনিম্ন ${Fmt.bn(wx.minC.roundToInt())}°")
                    }
                },
                Modifier.padding(top = 2.dp, bottom = 10.dp), fontSize = 15.sp, lineHeight = 22.sp, color = Ink,
            )
            // অবস্থান ও লাইভ লোকেশন
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Ico(Ic.pin, size = 18.dp, tint = Sky700)
                Text(wx.place.ifBlank { "অবস্থান জানা নেই" }, Modifier.weight(1f, fill = false).padding(start = 6.dp), fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val live = wx.locSrc == "gps"
                Text(if (live) "লাইভ" else "আনুমানিক", Modifier.padding(start = 6.dp).clip(CircleShape).background(if (live) Color(0xFFD1FAE5) else Color(0xFFE2E8F0)).padding(horizontal = 8.dp, vertical = 1.dp),
                    fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, color = if (live) Green else Slate, maxLines = 1, softWrap = false)
                Spacer(Modifier.weight(1f))
                Row(Modifier.bounceClick(true, onLive).clip(CircleShape).background(Sky100).padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Ico(Ic.locate, size = 15.dp, tint = Sky800, sw = 2.2f)
                    Text(" লাইভ লোকেশন", fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, color = Sky800, maxLines = 1, softWrap = false)
                }
            }
            // ৬টা তথ্য
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WxTile(Ic.drop, Sky100, Sky700, wx.humidity?.let { "${Fmt.bn(it)}%" } ?: "—", "আর্দ্রতা", Modifier.weight(1f)) {
                    val p by animateFloatAsState((wx.humidity ?: 0) / 100f, tween(1300, 400, FastOutSlowInEasing), label = "hum")
                    Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(4.dp).clip(CircleShape).background(Color(0xFFDBEAFE))) { Box(Modifier.fillMaxHeight().fillMaxWidth(p).background(Sky400)) }
                }
                WxTile(Ic.wind, Sky100, Sky700, wx.windKph?.let { "${Fmt.bn(it.roundToInt())} কিমি/ঘ" } ?: "—", "বাতাস" + dirBn(wx.windDir).let { if (it.isEmpty()) "" else " · $it" }, Modifier.weight(1f)) {
                    val sway by rememberInfiniteTransition(label = "sway").animateFloat(-12f, 12f, infiniteRepeatable(tween(3000), RepeatMode.Reverse), label = "sw")
                    Ico(Ic.up, Modifier.padding(top = 4.dp).graphicsLayer { rotationZ = dirDeg(wx.windDir) + 180f + sway }, size = 14.dp, tint = Sky700, sw = 2.4f)
                }
                WxTile(Ic.rain, Sky100, Sky700, wx.rainChance?.let { "${Fmt.bn(it)}%" } ?: "—", "বৃষ্টির সম্ভাবনা", Modifier.weight(1f))
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WxTile("M12 8 a4 4 0 1 0 0.01 0 M12 2 v2 M12 20 v2 M2 12 h2 M20 12 h2 M4.9 4.9 l1.4 1.4 M17.7 17.7 l1.4 1.4 M4.9 19.1 l1.4 -1.4 M17.7 6.3 l1.4 -1.4", Color(0xFFFEF3C7), Amber, wx.uv?.let { "${Fmt.bn(it.roundToInt())} · ${uvLabel(it)}" } ?: "—", "UV সূচক", Modifier.weight(1f))
                WxTile(Ic.leaf, Color(0xFFECFDF5), Green, aqiLabel(wx.aqi), "বায়ুর মান", Modifier.weight(1f))
                WxTile(Ic.dial, Color(0xFFF1F5F9), Slate, wx.pressure?.let { "${Fmt.bn(it.roundToInt())} hPa" } ?: "—", "বায়ুচাপ", Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            SunCard(wx.sunriseMin, wx.sunsetMin, wx.nowMin)
            if (wx.days.isNotEmpty()) {
                Text("আগামী ৩ দিন", Modifier.padding(start = 2.dp, top = 14.dp, bottom = 2.dp), fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, color = Gray)
                val lo = wx.days.minOf { it.minC }; val hi = wx.days.maxOf { it.maxC }
                wx.days.take(3).forEachIndexed { i, d -> ForecastRow(listOf("আজ", "কাল", "পরশু").getOrElse(i) { d.date }, d.maxC, d.minC, d.rain, d.code, lo, hi, i) }
            }
        }
    }
}

@Composable
private fun WxTile(icon: String, bg: Color, fg: Color, value: String, label: String, modifier: Modifier, extra: @Composable () -> Unit = {}) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Panel).padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 9.dp)) {
        Box(Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).background(bg), contentAlignment = Alignment.Center) { Ico(icon, size = 16.dp, tint = fg) }
        Text(value, Modifier.padding(top = 6.dp), fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold, color = Ink, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        Text(label, fontSize = 12.sp, lineHeight = 16.sp, color = Gray, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        extra()
    }
}

@Composable
private fun ForecastRow(day: String, hi: Double, lo: Double, rain: Int, code: Int, allLo: Double, allHi: Double, i: Int) {
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { go = true }
    val k by animateFloatAsState(if (go) 1f else 0f, tween(1100, 300 + i * 150, FastOutSlowInEasing), label = "fc")
    val span = (allHi - allLo).coerceAtLeast(1.0)
    val left = ((lo - allLo) / span).toFloat().coerceIn(0f, .95f)
    val width = ((hi - lo) / span).toFloat().coerceIn(.05f, 1f - left)
    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(day, Modifier.width(44.dp), fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, color = Ink, maxLines = 1)
        Ico(iconFor(code), size = 20.dp, tint = Sky700)
        Row(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(Color(0xFFE2E8F0))) {
            if (left > 0f) Spacer(Modifier.weight(left))
            Box(Modifier.weight(width).fillMaxHeight().graphicsLayer { scaleX = k; transformOrigin = TransformOrigin(0f, .5f) }.background(Brush.horizontalGradient(listOf(Sky400, Color(0xFFFBBF24)))))
            val right = 1f - left - width
            if (right > 0.001f) Spacer(Modifier.weight(right))
        }
        Text(buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Ink)) { append("${Fmt.bn(hi.roundToInt())}°") }
            withStyle(SpanStyle(color = Gray)) { append(" ${Fmt.bn(lo.roundToInt())}°") }
        }, Modifier.width(66.dp), fontSize = 14.sp, lineHeight = 20.sp, maxLines = 1, softWrap = false)
        Text("${Fmt.bn(rain)}%", Modifier.width(38.dp), fontSize = 12.sp, lineHeight = 16.sp, color = Sky700, maxLines = 1, softWrap = false)
    }
}

/** সচল সূর্যোদয়-সূর্যাস্ত: আঁকা হয়ে ওঠা সোনালি চাপ, ঘুরন্ত রশ্মি ও জ্বলজ্বলে সূর্য; রাতে চাঁদ ও ঝিকমিক তারা */
@Composable
private fun SunCard(sunrise: Int?, sunset: Int?, now: Int?) {
    if (sunrise == null || sunset == null || now == null || sunset <= sunrise) return
    val day = now in sunrise..sunset
    val prog = if (day) ((now - sunrise).toFloat() / (sunset - sunrise)).coerceIn(0f, 1f) else 0f
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { go = true }
    val p by animateFloatAsState(if (go) prog else 0f, tween(2400, 400, FastOutSlowInEasing), label = "sunp")
    val reveal by animateFloatAsState(if (go) 1f else 0f, tween(900, 1300), label = "reveal")
    val inf = rememberInfiniteTransition(label = "sun")
    val rot by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(14000, easing = LinearEasing)), label = "rays")
    val pulse by inf.animateFloat(.85f, 1.2f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "glow")
    val twinkle by inf.animateFloat(.2f, 1f, infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "star")
    val bg = if (day) Brush.verticalGradient(listOf(Color(0xFFFFF7E0), Panel)) else Brush.verticalGradient(listOf(Color(0xFF0F2A44), Color(0xFF1B4166)))
    val ink = if (day) Ink else Color.White
    val sub = if (day) Color(0xFF475569) else Color(0xFFBAE6FD)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(bg).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Canvas(Modifier.fillMaxWidth().height(72.dp)) {
            val w = size.width
            val base = size.height - 8.dp.toPx()
            val rx = (w - 24.dp.toPx()) / 2f
            val ry = 58.dp.toPx()
            val cx = w / 2f
            val tl = Offset(cx - rx, base - ry)
            val sz = Size(rx * 2, ry * 2)
            drawLine(sub.copy(alpha = .35f), Offset(4.dp.toPx(), base), Offset(w - 4.dp.toPx(), base), 1.5.dp.toPx())
            drawArc(sub.copy(alpha = .5f), 180f, 180f, false, tl, sz, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 9.dp.toPx()))))
            if (day) {
                drawArc(Brush.horizontalGradient(listOf(Color(0xFFFDE68A), Color(0xFFF59E0B)), startX = cx - rx, endX = cx + rx), 180f, 180f * p, false, tl, sz, style = Stroke(3.5.dp.toPx(), cap = StrokeCap.Round))
                val ang = Math.toRadians((180f + 180f * p).toDouble())
                val sc = Offset(cx + (rx * cos(ang)).toFloat(), base + (ry * sin(ang)).toFloat())
                drawCircle(Color(0xFFFBBF24).copy(alpha = .2f), 19.dp.toPx() * pulse, sc)
                drawCircle(Color(0xFFFBBF24).copy(alpha = .35f), 12.dp.toPx() * pulse, sc)
                for (i in 0 until 8) {
                    val a = Math.toRadians((rot + i * 45).toDouble())
                    drawLine(Color(0xFFF59E0B), Offset(sc.x + (9.dp.toPx() * cos(a)).toFloat(), sc.y + (9.dp.toPx() * sin(a)).toFloat()),
                        Offset(sc.x + (13.5.dp.toPx() * cos(a)).toFloat(), sc.y + (13.5.dp.toPx() * sin(a)).toFloat()), 1.8.dp.toPx(), StrokeCap.Round)
                }
                drawCircle(Color(0xFFFBBF24), 6.5.dp.toPx(), sc)
                drawCircle(Color.White.copy(alpha = .7f), 2.4.dp.toPx(), Offset(sc.x - 2.dp.toPx(), sc.y - 2.dp.toPx()))
            } else {
                val mc = Offset(cx, base - ry)
                drawCircle(Color(0xFFF8FAFC), 8.dp.toPx(), mc)
                drawCircle(Color(0xFF1B4166), 7.dp.toPx(), Offset(mc.x + 4.dp.toPx(), mc.y - 2.dp.toPx()))
                listOf(.18f to .28f, .30f to .7f, .72f to .35f, .84f to .66f, .55f to .2f).forEachIndexed { i, (fx, fy) ->
                    val a = ((twinkle + i * .2f) % 1f).coerceIn(.15f, 1f)
                    drawCircle(Color.White.copy(alpha = a), 1.6.dp.toPx(), Offset(w * fx, size.height * fy))
                }
            }
        }
        Row(Modifier.fillMaxWidth().graphicsLayer { alpha = reveal }, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(buildAnnotatedString { append("সূর্যোদয় "); withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = ink)) { append(hm(sunrise)) } }, fontSize = 13.sp, lineHeight = 18.sp, color = sub)
            Text(buildAnnotatedString { append("সূর্যাস্ত "); withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = ink)) { append(hm(sunset)) } }, fontSize = 13.sp, lineHeight = 18.sp, color = sub)
        }
        val note = if (day) "দিনের আলো ${hmText(sunset - sunrise)} · সূর্যাস্তে আর ${hmText(sunset - now)}"
        else "সূর্যোদয়ে আর ${hmText(if (now < sunrise) sunrise - now else 24 * 60 - now + sunrise)}"
        Text(note, Modifier.fillMaxWidth().padding(top = 2.dp).graphicsLayer { alpha = reveal }, fontSize = 12.sp, lineHeight = 16.sp, color = sub)
    }
}
