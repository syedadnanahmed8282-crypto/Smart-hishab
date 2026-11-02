package com.smarthisab.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.*
import com.smarthisab.app.domain.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

val Sky50 = Color(0xFFF0F9FF); val Sky100 = Color(0xFFE0F2FE); val Sky200 = Color(0xFFBAE6FD)
val Sky300 = Color(0xFF7DD3FC); val Sky400 = Color(0xFF38BDF8); val Sky500 = Color(0xFF0EA5E9)
val Sky600 = Color(0xFF0284C7); val Sky700 = Color(0xFF0369A1); val Sky800 = Color(0xFF075985)
val Navy = Color(0xFF082F49); val Slate = Color(0xFF475569); val Green = Color(0xFF047857)
val Rose = Color(0xFFBE123C); val Pink = Color(0xFFFDA4AF); val Amber = Color(0xFFB45309)
val Yellow = Color(0xFFFDE047); val Indigo = Color(0xFF4F46E5); val Red300 = Color(0xFFFCA5A5)
val BlueBrush = Brush.linearGradient(listOf(Color(0xFF0C6BA3), Sky700, Sky800))
val BtnBrush = Brush.linearGradient(listOf(Color(0xFF0A78B3), Sky800))
val RoseBrush = Brush.linearGradient(listOf(Color(0xFFE11D48), Color(0xFF9F1239)))
val IndigoBrush = Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFF3730A3)))

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(primary = Sky700, onPrimary = Color.White, secondary = Sky500, background = Sky50,
            surface = Color.White, onSurface = Navy, error = Rose),
        content = content,
    )
}

@Composable
fun Card3D(modifier: Modifier = Modifier, tint: Color = Color.White, border: Color = Sky100, pad: Dp = 14.dp, delay: Int = 0, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier.riseIn(delay).shadow(10.dp, shape, ambientColor = Sky500, spotColor = Sky500)
            .background(tint, shape).border(1.dp, border, shape).padding(pad),
        content = content,
    )
}

@Composable
fun BlueCard(modifier: Modifier = Modifier, delay: Int = 0, content: @Composable BoxScope.() -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Box(modifier.riseIn(delay).shadow(18.dp, shape, ambientColor = Sky800, spotColor = Sky800).clip(shape).background(BlueBrush).glossy(26.dp).shine(), content = content)
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, brush: Brush = BtnBrush, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    val bg = if (enabled) brush else Brush.linearGradient(listOf(Color(0xFF8FB9D2), Color(0xFF7FA9C2)))
    Box(
        modifier.height(50.dp).bounceClick(enabled, onClick)
            .shadow(if (enabled) 10.dp else 0.dp, shape, ambientColor = Sky800, spotColor = Sky800)
            .clip(shape).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        if (enabled) Box(Modifier.fillMaxWidth().height(22.dp).align(Alignment.TopCenter).background(Brush.verticalGradient(listOf(Color.White.copy(alpha = .24f), Color.Transparent))))
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
fun SoftButton(text: String, modifier: Modifier = Modifier, color: Color = Sky800, bg: Color = Color.White, icon: String? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier.height(50.dp).bounceClick(true, onClick).clip(shape).background(bg).border(1.dp, Sky200, shape).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) { Ico(icon, size = 18.dp, tint = color); Spacer(Modifier.width(8.dp)) }
        Text(text, color = color, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

/** জায়গায় না ধরলে লেখা নিজে ছোট হয়ে এক লাইনে বসে */
@Composable
fun FitText(text: String, modifier: Modifier = Modifier, maxSize: TextUnit = 14.sp, minSize: TextUnit = 8.sp, color: Color = Color.Unspecified, fontWeight: FontWeight? = null) {
    var size by remember(text) { mutableStateOf(maxSize) }
    Text(
        text, modifier, color = color, fontSize = size, fontWeight = fontWeight, maxLines = 1, softWrap = false,
        onTextLayout = { if (it.didOverflowWidth && size.value > minSize.value) size = (size.value - .5f).sp },
    )
}

@Composable
fun Chip(text: String, selected: Boolean = false, color: Color = Sky800, onClick: () -> Unit = {}) {
    Text(
        text, Modifier.bounceClick(true, onClick).clip(CircleShape).background(if (selected) Sky700 else Sky100).padding(horizontal = 12.dp, vertical = 6.dp),
        color = if (selected) Color.White else color, fontSize = 12.sp, fontWeight = FontWeight.Bold,
    )
}

@Composable
fun DeltaChip(pct: Double?, goodUp: Boolean = true, onBlue: Boolean = false) {
    if (pct == null) return
    val up = pct >= 0
    val good = up == goodUp
    val bg = if (onBlue) Color.White.copy(alpha = .18f) else if (good) Color(0xFFD1FAE5) else Color(0xFFFFE4E6)
    val fg = if (onBlue) (if (good) Color(0xFFA7F3D0) else Color(0xFFFECDD3)) else if (good) Green else Rose
    Text("${if (up) "▲" else "▼"} ${Fmt.bn(abs(pct).toLong())}%", Modifier.clip(CircleShape).background(bg).padding(horizontal = 8.dp, vertical = 2.dp),
        color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
}

@Composable
fun Banner(text: String, bold: String, bg: Color, border: Color, dot: Color, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth().riseIn(0).clip(RoundedCornerShape(16.dp)).background(bg).border(1.dp, border, RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
        Text(buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Navy)) { append(bold) }
            append(" · "); append(text)
        }, Modifier.weight(1f), fontSize = 13.sp, color = Slate)
        trailing()
    }
}

@Composable
fun TopBar(sub: String, title: String, back: (() -> Unit)? = null, right: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (back != null) {
            Box(Modifier.padding(end = 12.dp).size(40.dp).bounceClick(true, back).shadow(6.dp, RoundedCornerShape(14.dp)).background(Color.White, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                Ico(Ic.back, size = 22.dp, tint = Sky700, sw = 2.4f)
            }
        }
        Column(Modifier.weight(1f)) {
            if (sub.isNotEmpty()) Text(sub, fontSize = 13.sp, color = Color(0xFF0C4A6E))
            Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Navy)
        }
        right()
    }
}

@Composable
fun ScreenBox(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Sky200, Sky100, Sky50), endY = 900f)), content = content)
}

@Composable
fun Field(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, numeric: Boolean = false, password: Boolean = false, enabled: Boolean = true, suffix: String? = null) {
    var show by remember { mutableStateOf(false) }
    OutlinedTextField(
        value, onChange, modifier.fillMaxWidth(), enabled = enabled, singleLine = true, label = { Text(label) },
        suffix = suffix?.let { { Text(it) } },
        trailingIcon = if (password) ({
            IconButton({ show = !show }) { Ico(if (show) Ic.eyeOff else Ic.eye, size = 22.dp, tint = Slate) }
        }) else null,
        visualTransformation = if (password && !show) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = if (numeric) androidx.compose.ui.text.input.KeyboardType.Number else if (password) androidx.compose.ui.text.input.KeyboardType.Password else androidx.compose.ui.text.input.KeyboardType.Text),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Sky600, unfocusedBorderColor = Sky200, focusedContainerColor = Sky50, unfocusedContainerColor = Sky50),
    )
}

/** আকাশ, ঘুরন্ত সূর্যরশ্মি, ঝিকমিক তারা, ভাসা মেঘ, উড়ন্ত পাখি ও দোলা পাহাড় */
@Composable
fun HeroBackdrop(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "sky")
    val drift by t.animateFloat(-14f, 18f, infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Reverse), label = "drift")
    val glow by t.animateFloat(.7f, 1f, infiniteRepeatable(tween(4000), RepeatMode.Reverse), label = "glow")
    val sway by t.animateFloat(0f, 7f, infiniteRepeatable(tween(8000), RepeatMode.Reverse), label = "sway")
    val rays by t.animateFloat(0f, 360f, infiniteRepeatable(tween(40000, easing = LinearEasing)), label = "rays")
    val twinkle by t.animateFloat(.15f, 1f, infiniteRepeatable(tween(2600), RepeatMode.Reverse), label = "tw")
    val fly by t.animateFloat(0f, 1f, infiniteRepeatable(tween(16000, easing = LinearEasing)), label = "fly")
    Canvas(modifier.background(Brush.verticalGradient(listOf(Sky300, Sky200, Sky50)))) {
        val w = size.width
        val sun = Offset(w - 66.dp.toPx(), 96.dp.toPx())
        rotate(rays, sun) {
            for (i in 0 until 12) rotate(i * 30f, sun) {
                drawRect(Color.White.copy(alpha = .16f), Offset(sun.x - 3.dp.toPx(), sun.y - 96.dp.toPx()), Size(6.dp.toPx(), 70.dp.toPx()))
            }
        }
        drawCircle(Color.White.copy(alpha = .75f * glow), 46.dp.toPx(), sun)
        listOf(Offset(w * .55f, 44.dp.toPx()) to 4f, Offset(w * .87f, 168.dp.toPx()) to 3f, Offset(w * .5f, 130.dp.toPx()) to 2.5f).forEachIndexed { i, (o, r) ->
            val a = (twinkle + i * .3f).let { if (it > 1f) 2f - it else it }.coerceIn(.1f, 1f)
            val s = r.dp.toPx() * (.6f + a)
            val p = Path().apply { moveTo(o.x, o.y - s); lineTo(o.x + s, o.y); lineTo(o.x, o.y + s); lineTo(o.x - s, o.y); close() }
            drawPath(p, Color.White.copy(alpha = a))
        }
        fun cloud(x: Float, y: Float, cw: Float) =
            drawRoundRect(Color.White.copy(alpha = .9f), Offset(x + drift.dp.toPx(), y), Size(cw, cw * .27f), CornerRadius(cw))
        cloud(24.dp.toPx(), 150.dp.toPx(), 110.dp.toPx())
        cloud(w - 120.dp.toPx(), 196.dp.toPx(), 84.dp.toPx())
        fun tri(cx: Float, top: Float, wd: Float, ht: Float, c: Color) {
            val p = Path().apply { moveTo(cx, top); lineTo(cx + wd / 2, top + ht); lineTo(cx - wd / 2, top + ht); close() }
            drawPath(p, c)
        }
        val mx = w * .55f + sway.dp.toPx()
        tri(mx, 176.dp.toPx(), 270.dp.toPx(), 150.dp.toPx(), Sky300)
        tri(mx, 176.dp.toPx(), 64.dp.toPx(), 36.dp.toPx(), Color.White)
        tri(w * .1f, 226.dp.toPx(), 250.dp.toPx(), 100.dp.toPx(), Sky400.copy(alpha = .55f))
        tri(w * .88f, 240.dp.toPx(), 220.dp.toPx(), 90.dp.toPx(), Sky300.copy(alpha = .7f))
        fun bird(prog: Float, y0: Float, sc: Float) {
            val x = -30.dp.toPx() + prog * (w + 60.dp.toPx())
            val y = y0 + sin(prog * 6.28f * 3) * 6.dp.toPx()
            val flap = .35f + .65f * abs(sin(prog * 6.28f * 14))
            val s = 12.dp.toPx() * sc
            val p = Path().apply {
                moveTo(x - s, y + s * .15f); quadraticBezierTo(x - s * .5f, y - s * flap, x, y); quadraticBezierTo(x + s * .5f, y - s * flap, x + s, y + s * .15f)
            }
            drawPath(p, Color(0xFF0C4A6E), style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round))
        }
        bird(fly, 168.dp.toPx(), 1f)
        bird((fly + .5f) % 1f, 184.dp.toPx(), .75f)
    }
}

/** আয় (সাদা, চওড়া) ও খরচ (রঙিন, সরু) — একই রিংয়ে দুই স্তর, সাথে ঘুরন্ত আলোর বিন্দু */
@Composable
fun DualRing(income: Float, expense: Float, expenseColor: Color, size: Dp = 76.dp, center: @Composable BoxScope.() -> Unit = {}) {
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { go = true }
    val a by animateFloatAsState(if (go) income.coerceIn(0f, 1f) else 0f, tween(1500, easing = FastOutSlowInEasing), label = "ra")
    val b by animateFloatAsState(if (go) expense.coerceIn(0f, 1f) else 0f, tween(1700, 300, FastOutSlowInEasing), label = "rb")
    val orbit by rememberInfiniteTransition(label = "orb").animateFloat(0f, 360f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "o")
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = this.size.minDimension * .1f
            val tl = Offset(sw / 2, sw / 2)
            val sz = Size(this.size.width - sw, this.size.height - sw)
            drawArc(Color.White.copy(alpha = .22f), 0f, 360f, false, tl, sz, style = Stroke(sw))
            drawArc(Color.White, -90f, 360f * a, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
            drawArc(Sky800, -90f, 360f * b, false, tl, sz, style = Stroke(sw * .65f, cap = StrokeCap.Round))
            drawArc(expenseColor, -90f, 360f * b, false, tl, sz, style = Stroke(sw * .36f, cap = StrokeCap.Round))
            val ang = Math.toRadians((orbit - 90).toDouble())
            val r = (this.size.minDimension - sw) / 2f
            val c = Offset(this.size.width / 2f + (r * cos(ang)).toFloat(), this.size.height / 2f + (r * sin(ang)).toFloat())
            drawCircle(Color.White.copy(alpha = .35f), sw * .95f, c)
            drawCircle(Color.White, sw * .3f, c)
        }
        center()
    }
}

/** আয় ও খরচের দুই লাইনের গ্রাফ: লাইন আঁকা হয়, ডট লাফিয়ে আসে, একটা আলোর বিন্দু লাইন ধরে চলে */
@Composable
fun TwoLineChart(income: List<Double>, expense: List<Double>, labels: List<String>, modifier: Modifier = Modifier,
                 incomeColor: Color = Color.White, expenseColor: Color = Pink, gridColor: Color = Color.White.copy(alpha = .22f),
                 labelColor: Color = Sky100, dotFill: Color = Sky700) {
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { go = true }
    val p by animateFloatAsState(if (go) 1f else 0f, tween(1800, 500, FastOutSlowInEasing), label = "chart")
    val trav by rememberInfiniteTransition(label = "trav").animateFloat(0f, 1f, infiniteRepeatable(tween(7000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "t")
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val n = income.size
            if (n < 2) return@Canvas
            val maxV = (income + expense).maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
            val padX = 20.dp.toPx(); val top = 8.dp.toPx(); val h = size.height - top
            fun pt(i: Int, v: Double) = Offset(padX + i * (size.width - 2 * padX) / (n - 1), top + h - (v / maxV).toFloat() * (h - 4.dp.toPx()))
            for (g in 0..2) drawLine(gridColor, Offset(padX, top + h * g / 2), Offset(size.width - padX, top + h * g / 2), 1.dp.toPx())
            fun line(vals: List<Double>, c: Color): Path {
                val path = Path()
                vals.forEachIndexed { i, v -> val o = pt(i, v); if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y) }
                val pm = PathMeasure(); pm.setPath(path, false)
                val seg = Path(); pm.getSegment(0f, pm.length * p, seg, true)
                drawPath(seg, c, style = Stroke(2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                vals.forEachIndexed { i, v ->
                    if (i.toFloat() / (n - 1) <= p) { drawCircle(dotFill, 4.dp.toPx(), pt(i, v)); drawCircle(c, 4.dp.toPx(), pt(i, v), style = Stroke(2.dp.toPx())) }
                }
                return path
            }
            if (expense.isNotEmpty()) line(expense, expenseColor)
            val ip = line(income, incomeColor)
            if (p >= .99f) {
                val pm = PathMeasure(); pm.setPath(ip, false)
                val pos = pm.getPosition(pm.length * trav)
                drawCircle(incomeColor.copy(alpha = .3f), 9.dp.toPx(), pos)
                drawCircle(incomeColor, 4.5.dp.toPx(), pos)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
            labels.forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 12.sp, color = labelColor) }
        }
    }
}

/** লাল গোল সিল: CLOSED & PAID — ধপাস করে বসে */
@Composable
fun SealStamp(t: Totals, from: LocalDate, to: LocalDate, modifier: Modifier = Modifier) {
    val red = Color(0xFFB91C1C)
    val scale = remember { Animatable(2.4f) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(500)
        launch { alpha.animateTo(1f, tween(150)) }
        scale.animateTo(1f, spring(dampingRatio = .45f, stiffness = Spring.StiffnessMedium))
    }
    val shape = RoundedCornerShape(16.dp)
    Box(modifier.graphicsLayer { rotationZ = -6f; scaleX = scale.value; scaleY = scale.value; this.alpha = alpha.value }
        .background(Color.White.copy(alpha = .93f), shape).border(3.dp, red, shape).padding(5.dp)) {
        Column(Modifier.border(1.5.dp, red, RoundedCornerShape(11.dp)).padding(horizontal = 12.dp, vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("★ CLOSED & PAID ★", color = red, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, letterSpacing = 1.5.sp)
            HorizontalDivider(Modifier.padding(vertical = 4.dp), 1.5.dp, red)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(Modifier.weight(1f)) { SealCell("মোট মাল", "${Fmt.bn(t.pieces)} পিস", red); Spacer(Modifier.height(4.dp)); SealCell("মোট খরচ", Fmt.taka(t.expense), red) }
                Column(Modifier.weight(1f)) { SealCell("মোট টাকা (আয়)", Fmt.taka(t.income), red); Spacer(Modifier.height(4.dp)); SealCell("বাকি টাকা", Fmt.taka(t.profit), red) }
            }
            HorizontalDivider(Modifier.padding(top = 5.dp, bottom = 3.dp), 1.5.dp, red)
            Text("${Fmt.range(from, to)} ${Fmt.bn(to.year.toString())} · ${Fmt.bn(t.days)} দিন", color = red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SealCell(label: String, value: String, c: Color) {
    Column { Text(label, color = c, fontSize = 12.sp, fontWeight = FontWeight.SemiBold); Text(value, color = c, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold) }
}
