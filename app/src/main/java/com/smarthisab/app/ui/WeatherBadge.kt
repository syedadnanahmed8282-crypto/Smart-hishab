package com.smarthisab.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarthisab.app.data.Wx
import com.smarthisab.app.domain.Fmt
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * সূর্যের ওপর তাপমাত্রা: ঘড়ির মতো দাগসহ রঙিন গোল চাপ (০–৫০°C), মাঝে সংখ্যা, নিচে অবস্থা।
 * চাপলে চাপটা গুটিয়ে যায়, আলোর বিন্দু ঘোরে, তারপর নতুন তাপমাত্রায় আবার আঁকা হয়।
 */
@Composable
fun WeatherBadge(wx: Wx?, loading: Boolean, hasKey: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val navy = Color(0xFF0C4A6E)
    val orange = Color(0xFFFB923C)
    val frac = ((wx?.tempC ?: 0.0) / 50.0).coerceIn(0.03, 1.0).toFloat()
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val arc by animateFloatAsState(if (!shown || loading || wx == null) 0f else frac, tween(if (loading) 450 else 1700, easing = FastOutSlowInEasing), label = "arc")
    val num by animateFloatAsState(wx?.tempC?.toFloat() ?: 0f, tween(1300, easing = FastOutSlowInEasing), label = "num")
    val intro by animateFloatAsState(if (shown) 1f else 0f, spring(dampingRatio = .55f, stiffness = Spring.StiffnessLow), label = "intro")
    val inf = rememberInfiniteTransition(label = "wx")
    val comet by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "comet")
    val pulse by inf.animateFloat(1f, 1.07f, infiniteRepeatable(tween(3000), RepeatMode.Reverse), label = "pulse")
    val bob by inf.animateFloat(0f, 2f, infiniteRepeatable(tween(1300), RepeatMode.Reverse), label = "bob")
    val desc = if (wx != null) "তাপমাত্রা ${Fmt.bn(wx.tempC.roundToInt())} ডিগ্রি সেলসিয়াস, ${wx.text}। রিফ্রেশ করতে চাপুন" else "আবহাওয়া নেই। রিফ্রেশ করতে চাপুন"

    Column(modifier.graphicsLayer { val k = .6f + .4f * intro; scaleX = k; scaleY = k; alpha = intro.coerceIn(0f, 1f) }, horizontalAlignment = Alignment.End) {
        Box(Modifier.size(96.dp).align(Alignment.End).bounceClick(true, onClick).semantics { contentDescription = desc }, contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val c = center
                val outer = size.minDimension / 2f
                for (i in 0 until 36) {
                    val a = Math.toRadians((i * 10 - 90).toDouble())
                    val r1 = outer - 1.dp.toPx()
                    val r2 = r1 - (if (i % 3 == 0) 5.dp.toPx() else 3.dp.toPx())
                    drawLine(navy.copy(alpha = .3f), Offset(c.x + (r1 * cos(a)).toFloat(), c.y + (r1 * sin(a)).toFloat()),
                        Offset(c.x + (r2 * cos(a)).toFloat(), c.y + (r2 * sin(a)).toFloat()), 1.dp.toPx())
                }
                val r = outer - 10.dp.toPx()
                val sw = 5.dp.toPx()
                val tl = Offset(c.x - r, c.y - r)
                val sz = Size(r * 2, r * 2)
                drawCircle(Color.White.copy(alpha = .6f), r, c, style = Stroke(sw))
                if (arc > .001f) {
                    val f = arc.coerceAtLeast(.02f)
                    rotate(-90f, c) {
                        drawArc(Color(0xFFFBBF24).copy(alpha = .35f), 0f, 360f * arc, false, tl, sz, style = Stroke(sw * 1.8f, cap = StrokeCap.Round))
                        drawArc(Brush.sweepGradient(0f to Sky400, f * .55f to Yellow, f to orange, 1f to orange, center = c), 0f, 360f * arc, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
                    }
                    val ka = Math.toRadians((-90 + 360f * arc).toDouble())
                    val kp = Offset(c.x + (r * cos(ka)).toFloat(), c.y + (r * sin(ka)).toFloat())
                    drawCircle(orange.copy(alpha = .8f), 6.dp.toPx(), kp)
                    drawCircle(Color.White, 3.6.dp.toPx(), kp)
                }
                if (loading) {
                    rotate(comet, c) {
                        drawArc(Color.White.copy(alpha = .35f), -125f, 35f, false, tl, sz, style = Stroke(sw * 1.4f, cap = StrokeCap.Round))
                        drawArc(Color.White, -90f, 28f, false, tl, sz, style = Stroke(sw * .8f, cap = StrokeCap.Round))
                    }
                }
            }
            Text(
                buildAnnotatedString {
                    append(if (wx == null) "--" else Fmt.bn(num.roundToInt())); append("°")
                    withStyle(SpanStyle(fontSize = 13.sp)) { append("C") }
                },
                Modifier.graphicsLayer { scaleX = pulse; scaleY = pulse },
                style = TextStyle(fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, color = navy, shadow = Shadow(Color.White, Offset.Zero, 14f)),
                maxLines = 1, softWrap = false,
            )
        }
        Spacer(Modifier.height(4.dp))
        val old = wx != null && System.currentTimeMillis() - wx.at > 3 * 3600_000L
        val label = when {
            wx != null -> "${wx.text} · অনুভূত ${Fmt.bn(wx.feelsC.roundToInt())}°" + if (old) " · পুরনো" else ""
            !hasKey -> "আবহাওয়ার কী নেই"
            loading -> "আবহাওয়া আনা হচ্ছে…"
            else -> "আবহাওয়া নেই · চাপুন"
        }
        Row(
            Modifier.widthIn(max = 210.dp).clip(CircleShape).background(Color.White.copy(alpha = .75f)).border(1.dp, Color.White, CircleShape)
                .padding(start = 8.dp, end = 10.dp, top = 1.dp, bottom = 1.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Ico(if (wx?.code == 1000) Ic.sun else Ic.cloud, Modifier.graphicsLayer { translationX = bob; translationY = -bob * .7f }, size = 16.dp, tint = Sky700)
            Text(" $label", fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, color = navy, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        }
    }
}
