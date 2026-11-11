package com.smarthisab.app.ui

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.smarthisab.app.data.EntryE
import com.smarthisab.app.data.FolderE
import com.smarthisab.app.domain.*
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private val Bd = Color(0xFFE3EDF5)
private val Gy = Color(0xFF64748B)
private val Pale = Color(0xFFF3F8FC)
private val Deep = Sky800
private val Ink = Color(0xFF0F172A)

@Composable
fun ReportScreen(vm: AppVm, nav: NavHostController) {
    val f by vm.current.collectAsState()
    val es by vm.currentEntries.collectAsState()
    val all by vm.entries.collectAsState()
    val folders by vm.folders.collectAsState()
    var tab by rememberSaveable { mutableStateOf(0) }
    val today = remember { LocalDate.now() }
    val folder = f
    val prevF = folder?.let { cur -> folders.filter { it.id != cur.id && it.startDay < cur.startDay }.maxByOrNull { it.startDay } }
    val prevEs = prevF?.let { p -> all.filter { it.folderId == p.id } } ?: emptyList()

    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFDCEFFB), Pale), endY = 560f))) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (folder != null) "${folder.name} · ${Fmt.bn(Cycle.totalDays(folder, today))} দিন" else "", fontSize = 13.sp, lineHeight = 18.sp, color = Color(0xFF475569), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("রিপোর্ট", fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, color = Navy)
            }
            FolderPill(vm, nav, folder)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("সংক্ষিপ্ত", "বিশ্লেষণ", "বিস্তারিত", "রেকর্ড").forEachIndexed { i, t ->
                val on = i == tab
                Text(t, Modifier.weight(1f).bounceClick(true) { tab = i }.clip(CircleShape).background(if (on) Deep else Color.White).border(1.dp, if (on) Deep else Bd, CircleShape).padding(vertical = 8.dp),
                    textAlign = TextAlign.Center, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, color = if (on) Color.White else Gy, maxLines = 1, softWrap = false)
            }
        }
        if (folder == null) { Text("কোনো ফোল্ডার নেই", Modifier.padding(24.dp), color = Slate); return@Column }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (es.isEmpty()) Text("রিপোর্ট দেখতে আগে কিছু হিসাব দিন।", color = Slate)
            else when (tab) {
                0 -> SummaryTab(folder, es, today) { tab = it }
                1 -> AnalyticsTab(folder, es, today, prevF, prevEs)
                2 -> DetailTab(vm, folder, es, today)
                else -> RecordsTab(folder, es, today, prevF, prevEs)
            }
        }
    }
}

// ---------- সাধারণ উপাদান ----------
@Composable
private fun RCard(modifier: Modifier = Modifier, tint: Color = Color.White, pad: Dp = 14.dp, delay: Int = 0, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(modifier.fillMaxWidth().riseIn(delay).shadow(5.dp, shape, ambientColor = Sky500, spotColor = Sky500).background(tint, shape).border(1.dp, Bd, shape).padding(pad), content = content)
}

private fun axisTop(max: Double): Double {
    val s = max(max, 1.0) / 3.0
    val p = 10.0.pow(floor(log10(s)))
    val m = s / p
    return p * (if (m <= 1) 1.0 else if (m <= 2) 2.0 else if (m <= 2.5) 2.5 else if (m <= 5) 5.0 else 10.0) * 3
}

private fun growthPct(months: List<Block>, weeks: List<Block>): Double? {
    fun avg(b: Block) = (b.t.income - b.t.expense) / max(1, b.t.days)
    return when {
        months.size >= 2 -> Cycle.deltaPct(avg(months.last()), avg(months[months.size - 2]))
        weeks.size >= 2 -> Cycle.deltaPct(weeks.last().t.profit, weeks[weeks.size - 2].t.profit)
        else -> null
    }
}

@Composable
private fun Pill(text: String, bg: Color, fg: Color) {
    Text(text, Modifier.clip(CircleShape).background(bg).padding(horizontal = 10.dp, vertical = 1.dp), fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, color = fg, maxLines = 1, softWrap = false)
}

/** বাঁয়ে Y-অক্ষের সংখ্যা, ডানে চার্ট; এক সারিতে মেলানো */
@Composable
private fun ChartFrame(top: Double, height: Dp, chart: @Composable BoxScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().height(height)) {
        Column(Modifier.width(36.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.End) {
            for (k in 3 downTo 0) Text(Fmt.bn((top * k / 3).roundToLong()), fontSize = 12.sp, lineHeight = 16.sp, color = Gy, maxLines = 1, softWrap = false)
        }
        Box(Modifier.weight(1f).fillMaxHeight().padding(start = 6.dp), content = chart)
    }
}

@Composable
private fun Tooltip(text: String, x: Dp, y: Dp, scale: Float) {
    Text(text, Modifier.offset(x = x.coerceAtLeast(0.dp), y = y.coerceAtLeast(0.dp)).graphicsLayer { scaleX = scale; scaleY = scale; alpha = scale.coerceIn(0f, 1f); transformOrigin = TransformOrigin(.85f, 1f) }
        .clip(RoundedCornerShape(9.dp)).background(Deep).padding(horizontal = 10.dp, vertical = 3.dp),
        color = Color.White, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
}

/** নিট লাভের লাইন চার্ট: গ্রেডিয়েন্ট এরিয়া, আঁকা হওয়া লাইন, শেষ বিন্দুতে স্পন্দন ও টুলটিপ */
@Composable
private fun ProfitLine(values: List<Double>, labels: List<String>, labelAt: Set<Int>) {
    val top = axisTop((values.maxOrNull() ?: 1.0).coerceAtLeast(1.0))
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { go = true }
    val p by animateFloatAsState(if (go) 1f else 0f, tween(1800, 300, FastOutSlowInEasing), label = "pl")
    val tip by animateFloatAsState(if (go) 1f else 0f, tween(600, 1700, FastOutSlowInEasing), label = "tip")
    val pulse by rememberInfiniteTransition(label = "pp").animateFloat(0f, 1f, infiniteRepeatable(tween(2000, easing = LinearEasing)), label = "pp")
    Column {
        ChartFrame(top, 150.dp) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val wDp = maxWidth; val hDp = maxHeight
                Canvas(Modifier.fillMaxSize()) {
                    val n = values.size
                    val padV = 8.dp.toPx(); val h = size.height - 2 * padV
                    fun pt(i: Int, v: Double) = Offset((i + .5f) * size.width / n, padV + h - (v / top).toFloat().coerceIn(0f, 1f) * h)
                    for (g in 0..3) drawLine(Bd, Offset(0f, padV + h * g / 3), Offset(size.width, padV + h * g / 3), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 8.dp.toPx())))
                    val area = Path().apply {
                        moveTo(pt(0, values[0]).x, padV + h)
                        values.forEachIndexed { i, v -> lineTo(pt(i, v).x, pt(i, v).y) }
                        lineTo(pt(n - 1, values[n - 1]).x, padV + h); close()
                    }
                    drawPath(area, Brush.verticalGradient(listOf(Sky400.copy(alpha = .35f * p), Color.Transparent), startY = padV, endY = padV + h))
                    val line = Path()
                    values.forEachIndexed { i, v -> val o = pt(i, v); if (i == 0) line.moveTo(o.x, o.y) else line.lineTo(o.x, o.y) }
                    val pm = PathMeasure(); pm.setPath(line, false)
                    val seg = Path(); pm.getSegment(0f, pm.length * p, seg, true)
                    drawPath(seg, Deep, style = Stroke(2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    values.forEachIndexed { i, v ->
                        if (i.toFloat() / (n - 1) <= p) {
                            if (i == n - 1) {
                                drawCircle(Deep.copy(alpha = .5f * (1f - pulse)), 4.8.dp.toPx() * (1f + 1.6f * pulse), pt(i, v))
                                drawCircle(Deep, 4.8.dp.toPx(), pt(i, v))
                            } else { drawCircle(Color.White, 3.dp.toPx(), pt(i, v)); drawCircle(Deep, 3.dp.toPx(), pt(i, v), style = Stroke(2.dp.toPx())) }
                        }
                    }
                }
                val n = values.size
                val lastX = wDp * ((n - .5f) / n)
                val lastY = 8.dp + (hDp - 16.dp) * (1f - (values.last() / top).toFloat().coerceIn(0f, 1f))
                Tooltip("৳ ${Fmt.bn(values.last().roundToLong())}", lastX - 60.dp, lastY - 40.dp, tip)
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 42.dp)) {
            values.indices.forEach { i -> Text(if (i in labelAt) labels[i] else "", Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 12.sp, lineHeight = 16.sp, color = Gy, maxLines = 1, softWrap = false) }
        }
    }
}

/** এই চক্র (গাঢ়) বনাম আগের চক্র (হালকা) — সপ্তাহ অনুযায়ী; শেষ বারে টুলটিপ */
@Composable
private fun GroupedBars(cur: List<Double>, prev: List<Double>?, labels: List<String>) {
    val top = axisTop(((cur + (prev ?: emptyList())).maxOrNull() ?: 1.0).coerceAtLeast(1.0))
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { go = true }
    val p by animateFloatAsState(if (go) 1f else 0f, tween(1100, 300, FastOutSlowInEasing), label = "gb")
    val tip by animateFloatAsState(if (go) 1f else 0f, tween(600, 1400, FastOutSlowInEasing), label = "gt")
    Column {
        ChartFrame(top, 150.dp) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val wDp = maxWidth; val hDp = maxHeight
                val n = cur.size
                Canvas(Modifier.fillMaxSize()) {
                    val padV = 8.dp.toPx(); val h = size.height - 2 * padV
                    val slot = size.width / n; val bw = 20.dp.toPx(); val gap = 2.dp.toPx()
                    for (g in 0..3) drawLine(Bd, Offset(0f, padV + h * g / 3), Offset(size.width, padV + h * g / 3), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 8.dp.toPx())))
                    fun bar(x: Float, v: Double, c: Color) {
                        val bh = (v.coerceAtLeast(0.0) / top).toFloat().coerceIn(0f, 1f) * h * p
                        drawRoundRect(c, Offset(x, padV + h - bh), Size(bw, max(bh, 1f)), androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()))
                    }
                    for (i in 0 until n) {
                        val cx = (i + .5f) * slot
                        if (prev != null) { bar(cx - bw - gap, prev[i], Sky200); bar(cx + gap, cur[i], Deep) } else bar(cx - bw / 2, cur[i], Deep)
                    }
                }
                val lastC = wDp * ((n - .5f) / n)
                val barCx = if (prev != null) lastC + 2.dp + 10.dp else lastC
                val barTop = 8.dp + (hDp - 16.dp) * (1f - (cur.last() / top).toFloat().coerceIn(0f, 1f))
                Tooltip("৳ ${Fmt.bn(cur.last().roundToLong())}", barCx - 54.dp, barTop - 38.dp, tip)
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 42.dp)) {
            labels.forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 12.sp, lineHeight = 16.sp, color = Gy, maxLines = 1, softWrap = false) }
        }
    }
}

@Composable
private fun Donut(parts: List<Pair<Float, Color>>, size: Dp, center: @Composable BoxScope.() -> Unit) {
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { go = true }
    val a by animateFloatAsState(if (go) 1f else 0f, tween(1400, 400, FastOutSlowInEasing), label = "dn")
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = 18.dp.toPx()
            val tl = Offset(sw / 2, sw / 2); val sz = Size(this.size.width - sw, this.size.height - sw)
            drawArc(Color(0xFFEEF3F8), 0f, 360f, false, tl, sz, style = Stroke(sw))
            var start = -90f
            parts.forEach { (f, c) ->
                val sweep = 360f * f * a
                val gap = if (sweep > 4f) 1.5f else 0f
                drawArc(c, start + gap / 2, (sweep - gap).coerceAtLeast(0f), false, tl, sz, style = Stroke(sw))
                start += sweep
            }
        }
        center()
    }
}

@Composable
private fun Sparkline(values: List<Double>) {
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { go = true }
    val p by animateFloatAsState(if (go) 1f else 0f, tween(1500, 300, FastOutSlowInEasing), label = "sp")
    val pulse by rememberInfiniteTransition(label = "sp2").animateFloat(0f, 1f, infiniteRepeatable(tween(2000, easing = LinearEasing)), label = "sp2")
    Canvas(Modifier.size(width = 118.dp, height = 56.dp)) {
        if (values.size < 2) return@Canvas
        val lo = values.minOrNull() ?: 0.0; val hi = (values.maxOrNull() ?: 1.0).coerceAtLeast(lo + 1)
        val pad = 6.dp.toPx()
        fun pt(i: Int, v: Double) = Offset(pad + i * (size.width - 2 * pad) / (values.size - 1), size.height - pad - ((v - lo) / (hi - lo)).toFloat() * (size.height - 2 * pad))
        val line = Path(); values.forEachIndexed { i, v -> val o = pt(i, v); if (i == 0) line.moveTo(o.x, o.y) else line.lineTo(o.x, o.y) }
        val area = Path().apply { moveTo(pt(0, values[0]).x, size.height); values.forEachIndexed { i, v -> lineTo(pt(i, v).x, pt(i, v).y) }; lineTo(pt(values.size - 1, values.last()).x, size.height); close() }
        drawPath(area, Brush.verticalGradient(listOf(Sky400.copy(alpha = .4f * p), Color.Transparent)))
        val pm = PathMeasure(); pm.setPath(line, false)
        val seg = Path(); pm.getSegment(0f, pm.length * p, seg, true)
        drawPath(seg, Sky700, style = Stroke(2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        if (p >= .99f) { val e = pt(values.size - 1, values.last()); drawCircle(Sky700.copy(alpha = .5f * (1f - pulse)), 4.dp.toPx() * (1f + 1.6f * pulse), e); drawCircle(Sky700, 3.6.dp.toPx(), e) }
    }
}

@Composable
private fun ProgBar(frac: Float, delay: Int = 0) {
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { go = true }
    val a by animateFloatAsState(if (go) frac.coerceIn(0f, 1f) else 0f, tween(1100, 500 + delay * 150, FastOutSlowInEasing), label = "pb")
    Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Bd)) { Box(Modifier.fillMaxHeight().fillMaxWidth(a).clip(CircleShape).background(Brush.horizontalGradient(listOf(Sky400, Deep)))) }
}

// ---------- ১) সংক্ষিপ্ত ----------
@Composable
private fun SummaryTab(f: FolderE, es: List<EntryE>, today: LocalDate, onTab: (Int) -> Unit) {
    val tot = Cycle.totals(f, es, today)
    val g = growthPct(Cycle.blocks(f, es, today, 30), Cycle.blocks(f, es, today, 7))
    val d14 = Cycle.lastDays(f, es, today, 14)
    val net = d14.map { it.income - it.expense }
    val bob by rememberInfiniteTransition(label = "bob").animateFloat(0f, -3f, infiniteRepeatable(tween(1500), RepeatMode.Reverse), label = "b")
    RCard(tint = Color(0xFFEAF6FE), delay = 0) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.graphicsLayer { translationY = bob }.size(58.dp).shadow(10.dp, RoundedCornerShape(18.dp), ambientColor = Sky800, spotColor = Sky800).clip(RoundedCornerShape(18.dp)).background(BtnBrush), contentAlignment = Alignment.Center) {
                Ico(Ic.target, size = 30.dp, tint = Color.White, sw = 2.2f)
            }
            Column(Modifier.weight(1f)) {
                Text("নিট লাভ বৃদ্ধি", fontSize = 13.sp, lineHeight = 18.sp, color = Gy)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (g == null) "—" else "${if (g >= 0) "+" else "−"}${Fmt.bn(abs(g).roundToLong())}%", fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, color = Navy, maxLines = 1, softWrap = false)
                    DeltaChip(g)
                }
                Text("আগের কাজের মাসের তুলনায় (দিনপ্রতি)", fontSize = 12.sp, lineHeight = 16.sp, color = Gy, maxLines = 2)
            }
            Sparkline(net.takeLast(7))
        }
    }
    RCard(delay = 1) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("মূল সংখ্যা", fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text("সব দেখুন →", Modifier.bounceClick(true) { onTab(2) }, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, color = Sky700)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricTile(Ic.box, Sky100, Sky700, "${Fmt.bn(tot.pieces)}", "মোট মাল", Modifier.weight(1f))
            MetricTile(Ic.coin, Color(0xFFD1FAE5), Green, Fmt.taka(tot.income), "মোট আয়", Modifier.weight(1f))
            MetricTile(Ic.down, Color(0xFFFFE4E6), Rose, Fmt.taka(tot.expense), "মোট খরচ", Modifier.weight(1f))
        }
    }
    RCard(delay = 2) {
        Text("শেষ ১৪ দিনের নিট লাভ", fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(6.dp))
        ProfitLine(net, d14.map { Fmt.bn(it.date.dayOfMonth) }, setOf(0, 3, 6, 9, 13))
    }
}

@Composable
private fun MetricTile(icon: String, bg: Color, fg: Color, value: String, label: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Pale).padding(vertical = 10.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) { Ico(icon, size = 18.dp, tint = fg) }
        FitText(value, Modifier.fillMaxWidth().padding(top = 4.dp), maxSize = 16.sp, minSize = 9.sp, color = Ink, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 22.sp)
        Text(label, fontSize = 12.sp, lineHeight = 16.sp, color = Gy)
    }
}

// ---------- ২) বিশ্লেষণ ----------
@Composable
private fun AnalyticsTab(f: FolderE, es: List<EntryE>, today: LocalDate, prevF: FolderE?, prevEs: List<EntryE>) {
    val tot = Cycle.totals(f, es, today)
    val months = Cycle.blocks(f, es, today, 30)
    val weeks = Cycle.blocks(f, es, today, 7)
    val g = growthPct(months, weeks)
    val wk = weeks.takeLast(6)
    val prevWeeks = prevF?.let { Cycle.blocks(it, prevEs, today, 7) }
    val prevSeries = prevWeeks?.let { pw -> wk.map { w -> pw.getOrNull(w.no - 1)?.t?.profit ?: 0.0 } }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("নিট লাভ বিশ্লেষণ", fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
        Row(Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White).border(1.dp, Bd, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Ico(Ic.cal, size = 16.dp, tint = Gy); Text(" ${Fmt.range(Cycle.start(f), Cycle.end(f, today))} ", fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false); Ico(Ic.chev, size = 14.dp, tint = Gy)
        }
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FitText(Fmt.taka(tot.profit), Modifier.weight(1f, fill = false), maxSize = 34.sp, minSize = 18.sp, color = Navy, fontWeight = FontWeight.Bold, lineHeight = 42.sp)
            DeltaChip(g)
        }
        Text("আগের কাজের মাসের দিনপ্রতি গড়ের তুলনায়", fontSize = 13.sp, lineHeight = 18.sp, color = Gy)
    }
    RCard(pad = 12.dp, delay = 1) {
        Row(Modifier.padding(start = 4.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LegendDot(Deep, "এই চক্র"); if (prevSeries != null) LegendDot(Sky200, "আগের চক্র")
        }
        GroupedBars(wk.map { it.t.profit }, prevSeries, wk.map { "সপ্তাহ ${Fmt.bn(it.no)}" })
    }
    val pf = if (tot.income > 0) (tot.profit / tot.income).toFloat().coerceIn(0f, 1f) else 0f
    val ef = 1f - pf
    RCard(delay = 2) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Donut(listOf(pf to Deep, ef to Sky400), 118.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FitText(Fmt.taka(tot.income), Modifier.width(70.dp), maxSize = 15.sp, minSize = 9.sp, color = Ink, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text("মোট আয়", fontSize = 12.sp, lineHeight = 16.sp, color = Gy)
                }
            }
            Column(Modifier.weight(1f)) {
                Text("আয়ের বিভাজন", fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
                LegendRow(Deep, "বাকি (লাভ)", "${Fmt.bn((pf * 100).roundToInt())}%")
                LegendRow(Sky400, "খরচ", "${Fmt.bn((ef * 100).roundToInt())}%")
            }
        }
    }
    val pm = months.getOrNull(months.size - 2)
    val msg = if (pm != null) {
        val cm = months.last().t
        val di = Cycle.deltaPct(cm.avgIncome, pm.t.avgIncome) ?: 0.0
        val de = Cycle.deltaPct(cm.avgExpense, pm.t.avgExpense) ?: 0.0
        val dp = Cycle.deltaPct(cm.avgIncome - cm.avgExpense, pm.t.avgIncome - pm.t.avgExpense) ?: 0.0
        "দিনপ্রতি আয় ${Fmt.bn(abs(di).roundToLong())}% ${if (di >= 0) "বেড়েছে" else "কমেছে"}, খরচ ${Fmt.bn(abs(de).roundToLong())}% ${if (de >= 0) "বেড়েছে" else "কমেছে"}। তাই লাভ ${Fmt.bn(abs(dp).roundToLong())}% ${if (dp >= 0) "বেড়েছে" else "কমেছে"}।"
    } else "৩০ দিন পূর্ণ হলে আগের কাজের মাসের সাথে তুলনা দেখা যাবে।"
    RCard(tint = Color(0xFFE3F4FC), pad = 10.dp, delay = 3) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) { Ico(Ic.bulb, size = 18.dp, tint = Amber) }
            Text(msg, Modifier.weight(1f), fontSize = 13.sp, lineHeight = 19.sp, color = Color(0xFF0C4A6E))
        }
    }
}

@Composable private fun LegendDot(c: Color, t: String) { Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(c)); Text(" $t", fontSize = 12.sp, lineHeight = 16.sp, color = Gy) } }
@Composable private fun LegendRow(c: Color, t: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(c)); Text(" $t", Modifier.weight(1f), fontSize = 14.sp, lineHeight = 22.sp, color = Ink); Text(v, fontSize = 14.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
    }
}

// ---------- ৩) বিস্তারিত ----------
@Composable
private fun DetailTab(vm: AppVm, f: FolderE, es: List<EntryE>, today: LocalDate) {
    val ctx = LocalContext.current
    var scope by rememberSaveable { mutableStateOf(0) }
    var idx by rememberSaveable { mutableStateOf(-1) }
    val weeks = Cycle.blocks(f, es, today, 7)
    val months = Cycle.blocks(f, es, today, 30)
    val whole = listOf(Block(1, Cycle.start(f), Cycle.end(f, today), false, Cycle.totals(f, es, today)))
    val blocks = when (scope) { 0 -> weeks; 1 -> months; else -> whole }
    val i = (if (idx < 0) blocks.lastIndex else idx).coerceIn(0, blocks.lastIndex)
    val cur = blocks[i]
    val prev = blocks.getOrNull(i - 1)
    val t = cur.t
    val name = when (scope) { 0 -> "সপ্তাহ ${Fmt.bn(cur.no)}"; 1 -> "কাজের মাস ${Fmt.bn(cur.no)}"; else -> "পূর্ণ চক্র" }
    val title = "$name · ${Fmt.range(cur.from, cur.to)}"

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("সাপ্তাহিক", "কাজের মাস", "পূর্ণ চক্র").forEachIndexed { k, s ->
            val on = k == scope
            Text(s, Modifier.weight(1f).bounceClick(true) { scope = k; idx = -1 }.clip(CircleShape).background(if (on) Deep else Color.White).border(1.dp, if (on) Deep else Bd, CircleShape).padding(vertical = 7.dp),
                textAlign = TextAlign.Center, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, color = if (on) Color.White else Gy, maxLines = 1, softWrap = false)
        }
    }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).border(1.dp, Bd, RoundedCornerShape(16.dp)).padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).alpha2(i > 0).bounceClick(i > 0) { idx = i - 1 }, contentAlignment = Alignment.Center) { Ico(Ic.back, size = 18.dp, tint = Gy) }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Ico(Ic.cal, size = 18.dp, tint = Gy); Text(" $title", fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.size(36.dp).alpha2(i < blocks.lastIndex).bounceClick(i < blocks.lastIndex) { idx = i + 1 }, contentAlignment = Alignment.Center) { Ico(Ic.fwd, size = 18.dp, tint = Gy) }
    }
    RCard(pad = 0.dp, delay = 1) {
        DRow(Ic.box, Sky100, Sky700, "মোট মাল", Fmt.bn(t.pieces), prev?.let { Cycle.deltaPct(t.pieces.toDouble(), it.t.pieces.toDouble()) }, true, false)
        DRow(Ic.coin, Color(0xFFD1FAE5), Green, "মোট আয়", Fmt.taka(t.income), prev?.let { Cycle.deltaPct(t.income, it.t.income) }, true)
        DRow(Ic.down, Color(0xFFFFE4E6), Rose, "মোট খরচ", Fmt.taka(t.expense), prev?.let { Cycle.deltaPct(t.expense, it.t.expense) }, false)
        DRow(Ic.target, Sky100, Sky700, "নিট লাভ (আয় − খরচ)", Fmt.taka(t.profit), prev?.let { Cycle.deltaPct(t.profit, it.t.profit) }, true)
        DRow(Ic.cal, Color(0xFFFEF3C7), Amber, "কাজের দিন", "${Fmt.bn(t.work)} / ${Fmt.bn(t.days)}", null, true)
    }
    // সেরা ৩ দিন
    val inRange = es.filter { val d = LocalDate.ofEpochDay(it.day); !d.isBefore(cur.from) && !d.isAfter(cur.to) }.filter { it.quantity > 0 }.sortedByDescending { it.quantity }.take(3)
    val target = f.dailyTarget?.takeIf { it > 0 } ?: inRange.firstOrNull()?.quantity ?: 1
    RCard(pad = 14.dp, delay = 2) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("সেরা ৩ দিন", fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text(if (f.dailyTarget != null) "টার্গেট ${Fmt.bn(target)} পিস" else "", fontSize = 12.sp, lineHeight = 16.sp, color = Gy)
        }
        if (inRange.isEmpty()) Text("এই সময়ে কাজের দিন নেই", fontSize = 13.sp, color = Gy, modifier = Modifier.padding(top = 6.dp))
        inRange.forEachIndexed { k, e ->
            val pc = (e.quantity * 100f / target).roundToInt()
            Column(Modifier.padding(top = 8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${Fmt.date(LocalDate.ofEpochDay(e.day))} · ${Fmt.bn(e.quantity)} পিস", fontSize = 14.sp, lineHeight = 20.sp, color = Ink)
                    Text("${Fmt.bn(pc)}%", fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, color = Sky700)
                }
                Spacer(Modifier.height(4.dp)); ProgBar(pc / 100f, k)
            }
        }
    }
    // লক্ষ্য ও গতি
    val mb = months.last().t
    val goal = f.incomeGoal?.takeIf { it > 0 }; val limit = f.expenseLimit?.takeIf { it > 0 }
    RCard(pad = 14.dp, delay = 3) {
        Text("লক্ষ্য ও গতি (কাজের মাস ${Fmt.bn(months.last().no)})", fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
        if (goal != null) GoalLine("আয়", mb.income, goal, mb.avgIncome * 30, true)
        if (limit != null) GoalLine("খরচ", mb.expense, limit, mb.avgExpense * 30, false)
        if (goal == null && limit == null) Text("সেটিংস থেকে লক্ষ্য ও সীমা ঠিক করুন", fontSize = 13.sp, color = Gy, modifier = Modifier.padding(top = 6.dp))
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("গড় গতি ${Fmt.bn(t.avgPiecesPerWorkDay.roundToInt())} পিস/কাজের দিন", fontSize = 13.sp, lineHeight = 18.sp, color = Ink)
            DeltaChip(prev?.let { Cycle.deltaPct(t.avgPiecesPerWorkDay, it.t.avgPiecesPerWorkDay) })
        }
    }
    val pdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) {
            val ok = runCatching {
                writePdf(ctx, uri, "রিপোর্ট · ${f.name}", title, listOf(
                    "মোট মাল" to "${Fmt.bn(t.pieces)} পিস", "মোট আয়" to Fmt.taka(t.income), "মোট খরচ" to Fmt.taka(t.expense), "নিট লাভ (আয় − খরচ)" to Fmt.taka(t.profit),
                    "কাজের দিন" to Fmt.bn(t.work), "বন্ধের দিন" to Fmt.bn(t.off), "দিনপ্রতি গড় আয়" to Fmt.taka(t.avgIncome), "দিনপ্রতি গড় খরচ" to Fmt.taka(t.avgExpense),
                ), weeks.map { Triple("সপ্তাহ ${Fmt.bn(it.no)} · ${Fmt.range(it.from, it.to)}", "আয় ${Fmt.taka(it.t.income)} · খরচ ${Fmt.taka(it.t.expense)}", Fmt.taka(it.t.profit)) })
            }.isSuccess
            vm.say(if (ok) "PDF সংরক্ষিত হয়েছে" else "PDF বানানো যায়নি")
        }
    }
    PrimaryButton("রিপোর্ট ডাউনলোড (PDF)", Modifier.fillMaxWidth()) { pdf.launch("smart-hisab-report.pdf") }
}

private fun Modifier.alpha2(on: Boolean) = this.graphicsLayer { alpha = if (on) 1f else .3f }

@Composable
private fun DRow(icon: String, bg: Color, fg: Color, label: String, value: String, delta: Double?, goodUp: Boolean, divider: Boolean = true) {
    if (divider) HorizontalDivider(color = Color(0xFFEEF3F8))
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) { Ico(icon, size = 18.dp, tint = fg) }
        Text(label, Modifier.weight(1f), fontSize = 14.sp, lineHeight = 20.sp, color = Color(0xFF334155), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold, color = Ink, maxLines = 1, softWrap = false)
        Box(Modifier.width(64.dp), contentAlignment = Alignment.CenterEnd) { DeltaChip(delta, goodUp) }
    }
}

@Composable
private fun GoalLine(label: String, now: Double, goal: Double, forecast: Double, incomeKind: Boolean) {
    Column(Modifier.padding(top = 10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("$label ${Fmt.taka(now)} / ${Fmt.taka(goal)}", fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, color = Ink, maxLines = 1)
            Text("${Fmt.bn((now / goal * 100).roundToInt())}%", fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, color = if (incomeKind) Sky700 else Rose)
        }
        Spacer(Modifier.height(4.dp)); ProgBar((now / goal).toFloat())
        val over = !incomeKind && forecast > goal
        Text("এই গতিতে ৩০ দিনে ${Fmt.taka(forecast)} (${Fmt.bn((forecast / goal * 100).roundToInt())}%)" + if (over) " · সীমা পেরোতে পারে" else if (!incomeKind) " · সীমার ভেতরে" else if (forecast < goal) " · লক্ষ্যের চেয়ে কম" else " · লক্ষ্যের পথে",
            Modifier.padding(top = 2.dp), fontSize = 12.sp, lineHeight = 16.sp, color = Gy)
    }
}

/** সহজ PDF: শিরোনাম, মূল সংখ্যা ও সপ্তাহের তালিকা — ফাইল কোথায় রাখবেন সেটা ব্যবহারকারী বাছেন */
private fun writePdf(ctx: Context, uri: Uri, title: String, sub: String, rows: List<Pair<String, String>>, weeks: List<Triple<String, String, String>>) {
    val doc = PdfDocument()
    val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
    val c = page.canvas
    val p = Paint().apply { isAntiAlias = true }
    p.color = 0xFF082F49.toInt(); p.textSize = 22f; p.isFakeBoldText = true; c.drawText(title, 40f, 64f, p)
    p.color = 0xFF64748B.toInt(); p.textSize = 13f; p.isFakeBoldText = false; c.drawText(sub, 40f, 86f, p)
    var y = 124f
    p.textSize = 14f
    rows.forEach { (k, v) ->
        p.color = 0xFF334155.toInt(); c.drawText(k, 40f, y, p)
        p.color = 0xFF0F172A.toInt(); p.isFakeBoldText = true; c.drawText(v, 555f - p.measureText(v), y, p); p.isFakeBoldText = false
        p.color = 0xFFE3EDF5.toInt(); c.drawLine(40f, y + 10f, 555f, y + 10f, p); y += 28f
    }
    y += 16f
    p.color = 0xFF075985.toInt(); p.textSize = 15f; p.isFakeBoldText = true; c.drawText("সপ্তাহ অনুযায়ী", 40f, y, p); y += 24f
    p.isFakeBoldText = false; p.textSize = 12.5f
    weeks.take(24).forEach { (a, b, v) ->
        p.color = 0xFF0F172A.toInt(); c.drawText(a, 40f, y, p)
        p.color = 0xFF64748B.toInt(); c.drawText(b, 230f, y, p)
        p.color = 0xFF0F172A.toInt(); p.isFakeBoldText = true; c.drawText(v, 555f - p.measureText(v), y, p); p.isFakeBoldText = false
        y += 22f
    }
    doc.finishPage(page)
    ctx.contentResolver.openOutputStream(uri)?.use { doc.writeTo(it) }
    doc.close()
}

// ---------- ৪) রেকর্ড ----------
private data class Rec(val title: String, val sub: String, val profit: Double, val running: Boolean, val prevCycle: Boolean)

@Composable
private fun RecordsTab(f: FolderE, es: List<EntryE>, today: LocalDate, prevF: FolderE?, prevEs: List<EntryE>) {
    var query by rememberSaveable { mutableStateOf("") }
    var savedOnly by rememberSaveable { mutableStateOf(false) }
    val weeks = Cycle.blocks(f, es, today, 7)
    val recs = buildList {
        weeks.reversed().forEachIndexed { i, w ->
            val run = i == 0 && f.endDay == null
            add(Rec("সপ্তাহ ${Fmt.bn(w.no)} · ${Fmt.range(w.from, w.to)}", if (run) "চলমান · আজ রাতে সংরক্ষণ" else "${Fmt.bn(w.t.work)} কাজের দিন · ${Fmt.bn(w.t.off)} বন্ধ", w.t.profit, run, false))
        }
        prevF?.let { p -> Cycle.blocks(p, prevEs, today, 7).reversed().take(4).forEach { w -> add(Rec("আগের চক্র · ${Fmt.range(w.from, w.to)}", p.name, w.t.profit, false, true)) } }
    }.filter { (!savedOnly || !it.running) && (query.isBlank() || it.title.contains(query.trim()) || it.sub.contains(query.trim())) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.weight(1f).height(44.dp).clip(CircleShape).background(Color.White).border(1.dp, Bd, CircleShape).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Ico(Ic.search, size = 18.dp, tint = Gy)
            BasicTextField(query, { query = it }, Modifier.weight(1f).padding(start = 8.dp), singleLine = true, textStyle = TextStyle(fontSize = 14.sp, color = Ink), cursorBrush = SolidColor(Sky700),
                decorationBox = { inner -> Box(contentAlignment = Alignment.CenterStart) { if (query.isEmpty()) Text("রিপোর্ট খুঁজুন…", fontSize = 14.sp, color = Color(0xFF94A3B8)); inner() } })
        }
        Box(Modifier.size(44.dp).bounceClick(true) { savedOnly = !savedOnly }.clip(CircleShape).background(if (savedOnly) Deep else Color.White).border(1.dp, if (savedOnly) Deep else Bd, CircleShape), contentAlignment = Alignment.Center) {
            Ico(Ic.sort, size = 18.dp, tint = if (savedOnly) Color.White else Deep)
        }
    }
    if (savedOnly) Text("শুধু সংরক্ষিত রিপোর্ট দেখানো হচ্ছে", fontSize = 12.sp, lineHeight = 16.sp, color = Gy)
    RCard(pad = 0.dp, delay = 1) {
        if (recs.isEmpty()) Text("কোনো রিপোর্ট পাওয়া যায়নি", Modifier.padding(16.dp), color = Gy, fontSize = 14.sp)
        recs.forEachIndexed { i, r ->
            if (i > 0) HorizontalDivider(color = Color(0xFFEEF3F8))
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(Sky100), contentAlignment = Alignment.Center) { Ico(if (r.prevCycle) Ic.clock else Ic.file, size = 20.dp, tint = Sky700) }
                Column(Modifier.weight(1f)) {
                    Text(r.title, fontSize = 14.5.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(r.sub, fontSize = 12.sp, lineHeight = 16.sp, color = Gy, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(Fmt.taka(r.profit), fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold, color = if (r.profit >= 0) Ink else Rose, maxLines = 1, softWrap = false)
                    if (r.running) Pill("চলমান", Color(0xFFDBEAFE), Color(0xFF1D4ED8)) else Pill("সংরক্ষিত", Color(0xFFD1FAE5), Green)
                }
                Ico(Ic.chevR, size = 16.dp, tint = Color(0xFF94A3B8))
            }
        }
    }
}
