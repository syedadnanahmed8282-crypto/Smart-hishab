package com.smarthisab.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.smarthisab.app.data.EntryE
import com.smarthisab.app.data.FolderE
import com.smarthisab.app.domain.*
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun ReportScreen(vm: AppVm, nav: NavHostController) {
    val f by vm.current.collectAsState()
    val es by vm.currentEntries.collectAsState()
    var tab by rememberSaveable { mutableStateOf(0) }
    val today = remember { LocalDate.now() }
    val folder = f
    ScreenBox {
        TopBar(if (folder != null) "${Fmt.bn(Cycle.totalDays(folder, es, today))} দিনের চক্র" else "", "রিপোর্ট", right = { FolderPill(vm, nav, folder) })
        if (folder == null) { Text("কোনো ফোল্ডার নেই", Modifier.padding(24.dp), color = Slate); return@ScreenBox }
        Row(
            Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).border(1.dp, Sky100, RoundedCornerShape(18.dp)).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            listOf("সাপ্তাহিক", "লক্ষ্য ও গতি", "মাসিক", "রেকর্ড").forEachIndexed { i, t ->
                Text(t, Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if (i == tab) BtnBrush else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
                    .clickable { tab = i }.padding(vertical = 9.dp), textAlign = TextAlign.Center, fontSize = 12.5.sp,
                    fontWeight = if (i == tab) FontWeight.Bold else FontWeight.Medium, color = if (i == tab) Color.White else Slate)
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (es.isEmpty()) Text("রিপোর্ট দেখতে আগে কিছু হিসাব দিন।", color = Slate)
            else when (tab) {
                0 -> WeeklyTab(folder, es, today)
                1 -> GoalTab(folder, es, today)
                2 -> MonthlyTab(folder, es, today)
                else -> RecordsTab(folder, es, today)
            }
        }
    }
}

private fun dayProfit(es: List<EntryE>, d: LocalDate): Double =
    es.filter { it.deletedAt == null && it.day == d.toEpochDay() }.sumOf { incomeOf(it) - it.expense }
private fun pctOf(v: Double, total: Double) = if (total <= 0) 0f else (v / total).toFloat()

@Composable
private fun WeeklyTab(f: FolderE, es: List<EntryE>, today: LocalDate) {
    val weeks = Cycle.blocks(f, es, today, 7)
    val cur = weeks.last(); val prev = weeks.getOrNull(weeks.size - 2)
    val c = cur.t; val p = prev?.t
    BlueCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("সপ্তাহ ${Fmt.bn(cur.no)} · ${Fmt.range(cur.from, cur.to)}${if (cur.partial) " (চলমান)" else ""}", color = Sky100, fontSize = 13.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(Fmt.taka(c.profit), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                if (p != null) DeltaChip(Cycle.deltaPct(c.profit, p.profit), true, true)
            }
            if (p != null) Text("আগের সপ্তাহ ${Fmt.taka(p.profit)}", color = Sky100, fontSize = 12.sp)
            HorizontalDivider(Modifier.padding(vertical = 10.dp), color = Color.White.copy(alpha = .22f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeroStat("আয়", Fmt.taka(c.income), p?.let { Cycle.deltaPct(c.income, it.income) }, true, Modifier.weight(1f))
                HeroStat("খরচ", Fmt.taka(c.expense), p?.let { Cycle.deltaPct(c.expense, it.expense) }, false, Modifier.weight(1f))
                HeroStat("মাল", Fmt.bn(c.pieces), p?.let { Cycle.deltaPct(c.pieces.toDouble(), it.pieces.toDouble()) }, true, Modifier.weight(1f))
            }
        }
    }
    val curDates = cur.dates
    val prevDates = prev?.dates ?: emptyList()
    val curProfits = (0..6).map { i -> curDates.getOrNull(i)?.let { dayProfit(es, it) } ?: 0.0 }
    val prevProfits = (0..6).map { i -> prevDates.getOrNull(i)?.let { dayProfit(es, it) } ?: 0.0 }
    val labels = (0..6).map { i ->
        curDates.getOrNull(i)?.let { Fmt.weekday(it) }
            ?: prevDates.getOrNull(i)?.let { Fmt.weekday(it) }
            ?: Fmt.bn(i + 1)
    }
    Card3D(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("দৈনিক লাভ-লস", fontWeight = FontWeight.Bold, color = Navy, fontSize = 14.sp)
            Text("■ এই সপ্তাহ  ", color = Sky700, fontSize = 12.sp); 
        }
        PairBars(curProfits, prevProfits, labels)
        Text("লস হলে বার লাল হয়ে লাইনের নিচে নামবে। হালকা বার = আগের সপ্তাহ", fontSize = 12.sp, color = Slate)
    }
    if (p != null) Card3D(Modifier.fillMaxWidth()) {
        Text("আগের সপ্তাহের সাথে তুলনা", fontWeight = FontWeight.Bold, color = Navy, fontSize = 14.sp)
        CompareRow("আয়", Fmt.taka(c.income), Fmt.taka(p.income), Cycle.deltaPct(c.income, p.income), true)
        CompareRow("খরচ", Fmt.taka(c.expense), Fmt.taka(p.expense), Cycle.deltaPct(c.expense, p.expense), false)
        CompareRow("নিট লাভ", Fmt.taka(c.profit), Fmt.taka(p.profit), Cycle.deltaPct(c.profit, p.profit), true)
        CompareRow("মাল", Fmt.bn(c.pieces), Fmt.bn(p.pieces), Cycle.deltaPct(c.pieces.toDouble(), p.pieces.toDouble()), true)
        CompareRow("কাজের দিন", Fmt.bn(c.work), Fmt.bn(p.work), null, true)
    } else Text("আগের সপ্তাহ নেই, ৭ দিন পূর্ণ হলে তুলনা দেখা যাবে।", color = Slate, fontSize = 13.sp)
}

@Composable
private fun HeroStat(label: String, value: String, d: Double?, goodUp: Boolean, modifier: Modifier) {
    Column(modifier) {
        Text(label, color = Sky100, fontSize = 12.sp)
        Text(value, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        DeltaChip(d, goodUp, true)
    }
}

@Composable
private fun CompareRow(label: String, a: String, b: String, d: Double?, goodUp: Boolean) {
    HorizontalDivider(color = Sky100)
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1.1f), color = Slate, fontSize = 13.sp)
        Text(a, Modifier.weight(1f), color = Navy, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text(b, Modifier.weight(1f), color = Color(0xFF64748B), fontSize = 13.sp)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { DeltaChip(d, goodUp) }
    }
}

@Composable
private fun PairBars(cur: List<Double>, prev: List<Double>, labels: List<String>) {
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { go = true }
    val p by animateFloatAsState(if (go) 1f else 0f, tween(1100, easing = FastOutSlowInEasing), label = "bars")
    Column {
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(110.dp).padding(top = 8.dp)) {
            val all = cur + prev
            val maxV = max(all.maxOrNull() ?: 1.0, 1.0); val minV = minOf(all.minOrNull() ?: 0.0, 0.0)
            val zeroY = size.height * (maxV / (maxV - minV)).toFloat()
            val slot = size.width / cur.size
            val bw = slot * .26f
            fun bar(i: Int, off: Float, v: Double, c: Color) {
                val h = (abs(v) / (maxV - minV)).toFloat() * size.height * p
                val x = slot * i + slot / 2 + off
                drawRoundRect(c, Offset(x, if (v >= 0) zeroY - h else zeroY), Size(bw, max(h, 2f)), CornerRadius(6.dp.toPx()))
            }
            drawLine(Sky200, Offset(0f, zeroY), Offset(size.width, zeroY), 1.dp.toPx())
            cur.indices.forEach { i ->
                bar(i, -bw - 2.dp.toPx(), prev[i], Sky200)
                bar(i, 2.dp.toPx(), cur[i], if (cur[i] < 0) Rose else Sky600)
            }
        }
        Row(Modifier.fillMaxWidth()) { labels.forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 12.sp, color = Slate) } }
    }
}

@Composable
private fun ProgBar(p: Float, forecast: Float, color: Color, ghost: Color) {
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { go = true }
    val a by animateFloatAsState(if (go) p.coerceIn(0f, 1f) else 0f, tween(1300, easing = FastOutSlowInEasing), label = "p")
    val b by animateFloatAsState(if (go) forecast.coerceIn(0f, 1f) else 0f, tween(1300, 200, FastOutSlowInEasing), label = "f")
    Box(Modifier.fillMaxWidth().height(12.dp).clip(CircleShape).background(Sky100)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(b).background(ghost.copy(alpha = .35f)))
        Box(Modifier.fillMaxHeight().fillMaxWidth(a).clip(CircleShape).background(color))
    }
}

@Composable
private fun GoalTab(f: FolderE, es: List<EntryE>, today: LocalDate) {
    val cur = Cycle.blocks(f, es, today, 30).last()
    val t = cur.t
    val perDayInc = t.income / max(1, t.days); val perDayExp = t.expense / max(1, t.days)
    val goal = f.incomeGoal?.takeIf { it > 0 }; val limit = f.expenseLimit?.takeIf { it > 0 }
    Card3D(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("মাসিক আয়ের লক্ষ্য", Modifier.weight(1f), fontWeight = FontWeight.Bold, color = Navy)
            if (goal != null) { val ok = perDayInc * 30 >= goal; Text(if (ok) "লক্ষ্যের পথে" else "পিছিয়ে আছে", Modifier.clip(CircleShape).background(if (ok) Color(0xFFD1FAE5) else Color(0xFFFEF3C7)).padding(horizontal = 10.dp, vertical = 2.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (ok) Green else Amber) }
        }
        if (goal == null) Text("সেটিংস থেকে লক্ষ্য ঠিক করুন", color = Slate, fontSize = 13.sp) else {
            Text("${Fmt.taka(t.income)} / ${Fmt.taka(goal)} · ${Fmt.bn((t.income / goal * 100).roundToInt())}%", Modifier.padding(vertical = 6.dp), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Sky800)
            ProgBar(pctOf(t.income, goal), pctOf(perDayInc * 30, goal), Sky600, Sky400)
            Text("এই গতিতে ৩০ দিনে আনুমানিক ${Fmt.taka(perDayInc * 30)} (${Fmt.bn((perDayInc * 30 / goal * 100).roundToInt())}%)", Modifier.padding(top = 6.dp), fontSize = 12.sp, color = Slate)
        }
    }
    Card3D(Modifier.fillMaxWidth()) {
        val near = limit != null && t.expense / limit >= .8
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("মাসিক খরচের সীমা", Modifier.weight(1f), fontWeight = FontWeight.Bold, color = Navy)
            if (near) Text("সতর্কতা ⚠", Modifier.clip(CircleShape).background(Color(0xFFFEF3C7)).padding(horizontal = 10.dp, vertical = 2.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Amber)
        }
        if (limit == null) Text("সেটিংস থেকে সীমা ঠিক করুন", color = Slate, fontSize = 13.sp) else {
            Text("${Fmt.taka(t.expense)} / ${Fmt.taka(limit)} · ${Fmt.bn((t.expense / limit * 100).roundToInt())}%", Modifier.padding(vertical = 6.dp), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (near) Amber else Rose)
            ProgBar(pctOf(t.expense, limit), pctOf(perDayExp * 30, limit), if (near) Color(0xFFD97706) else Color(0xFFE11D48), Color(0xFFF59E0B))
            val fc = perDayExp * 30
            Text("এই গতিতে ৩০ দিনে ${Fmt.taka(fc)} (${Fmt.bn((fc / limit * 100).roundToInt())}%)" + if (fc > limit) " · সীমা ${Fmt.taka(fc - limit)} পেরোতে পারে" else " · সীমার ভেতরেই থাকবে", Modifier.padding(top = 6.dp), fontSize = 12.sp, color = Slate)
        }
    }
    val weeks = Cycle.blocks(f, es, today, 7); val w = weeks.last(); val pw = weeks.getOrNull(weeks.size - 2)
    val target = f.dailyTarget?.takeIf { it > 0 }
    val inWeek = if (w.dates.isNotEmpty()) es.filter { it.deletedAt == null && LocalDate.ofEpochDay(it.day) in w.dates }
                 else es.filter { val d = LocalDate.ofEpochDay(it.day); it.deletedAt == null && !d.isBefore(w.from) && !d.isAfter(w.to) }
    val inWeekByDay = inWeek.groupBy { it.day }
    val inWeekTargetMet = if (target != null) inWeekByDay.count { (_, list) -> list.sumOf { it.quantity } >= target } else 0
    val best = inWeekByDay.maxByOrNull { (_, list) -> list.sumOf { it.quantity } }
    Card3D(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("কাজের গতি", Modifier.weight(1f), fontWeight = FontWeight.Bold, color = Navy)
            if (target != null) Text("টার্গেট ${Fmt.bn(target)} পিস", fontSize = 12.sp, color = Slate)
        }
        Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(Fmt.bn(w.t.avgPiecesPerWorkDay.roundToInt()), fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = Sky800)
            Column {
                Text("পিস/কাজের দিন", fontSize = 12.sp, color = Slate)
                if (pw != null) DeltaChip(Cycle.deltaPct(w.t.avgPiecesPerWorkDay, pw.t.avgPiecesPerWorkDay))
            }
        }
        if (target != null) Text("টার্গেট পূরণ ${Fmt.bn(inWeekTargetMet)}/${Fmt.bn(w.t.work)} দিন", fontSize = 13.sp, color = Navy)
        if (best != null) Text("সেরা দিন ${Fmt.date(LocalDate.ofEpochDay(best.key))} · ${Fmt.bn(best.value.sumOf { it.quantity })} পিস", fontSize = 13.sp, color = Navy)
        val last7 = Cycle.lastDays(f, es, today)
        TwoLineChart(last7.map { it.pieces.toDouble() }, if (target != null) List(7) { target.toDouble() } else emptyList(), last7.map { Fmt.weekday(it.date) },
            Modifier.fillMaxWidth().height(110.dp).padding(top = 8.dp), Sky600, Color(0xFFD97706), Sky100, Slate, Color.White)
        if (target != null) Text("- - - টার্গেট লাইন", fontSize = 12.sp, color = Amber)
    }
}

@Composable
private fun MonthlyTab(f: FolderE, es: List<EntryE>, today: LocalDate) {
    val months = Cycle.blocks(f, es, today, 30)
    val cur = months.last(); val prev = months.getOrNull(months.size - 2)
    val t = cur.t
    Text("কাজের মাস ${Fmt.bn(cur.no)} (${Fmt.range(cur.from, cur.to)})${if (prev != null) " বনাম মাস ${Fmt.bn(prev.no)}" else ""} · দিনপ্রতি গড়ে", fontWeight = FontWeight.Bold, color = Navy, fontSize = 14.sp)
    val pt = prev?.t
    val rows = listOf(
        Triple("আয় / দিন", t.avgIncome to pt?.avgIncome, true), Triple("খরচ / দিন", t.avgExpense to pt?.avgExpense, false),
        Triple("লাভ / দিন", (t.avgIncome - t.avgExpense) to pt?.let { it.avgIncome - it.avgExpense }, true), Triple("মাল / দিন", t.avgPieces to pt?.avgPieces, true),
    )
    rows.chunked(2).forEach { pair ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            pair.forEach { (label, v, good) ->
                Card3D(Modifier.weight(1f), pad = 10.dp) {
                    Text(label, fontSize = 12.sp, color = Slate)
                    Text(if (label.startsWith("মাল")) Fmt.bn(v.first.roundToInt()) else Fmt.taka(v.first), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Navy)
                    v.second?.let { pv ->
                        Text("আগের ${if (label.startsWith("মাল")) Fmt.bn(pv.roundToInt()) else Fmt.taka(pv)}", fontSize = 12.sp, color = Color(0xFF64748B))
                        DeltaChip(Cycle.deltaPct(v.first, pv), good)
                    }
                }
            }
        }
    }
    if (prev == null) Text("আগের কাজের মাস নেই, ৩০ দিন পূর্ণ হলে তুলনা দেখা যাবে।", color = Slate, fontSize = 13.sp)
    val weeks = Cycle.blocks(f, es, today, 7).filter { !it.from.isBefore(cur.from) && !it.from.isAfter(cur.to) }
    if (weeks.isNotEmpty()) Card3D(Modifier.fillMaxWidth()) {
        Text("সপ্তাহ অনুযায়ী নিট লাভ", fontWeight = FontWeight.Bold, color = Navy, fontSize = 14.sp)
        PairBars(weeks.map { it.t.profit }, weeks.map { 0.0 }, weeks.map { "সপ্তাহ ${Fmt.bn(it.no)}" })
    }
    Card3D(Modifier.fillMaxWidth()) {
        Text("কাজ ও বন্ধের দিন", fontWeight = FontWeight.Bold, color = Navy, fontSize = 14.sp)
        Text("কাজ ${Fmt.bn(t.work)} · বন্ধ ${Fmt.bn(t.off)} · মোট ${Fmt.bn(t.days)} দিন", fontSize = 13.sp, color = Slate)
        Row(Modifier.padding(top = 6.dp).fillMaxWidth().height(12.dp).clip(CircleShape)) {
            if (t.work > 0) Box(Modifier.weight(t.work.toFloat()).fillMaxHeight().background(Sky600))
            if (t.off > 0) Box(Modifier.weight(t.off.toFloat()).fillMaxHeight().background(Color(0xFFFB7185)))
        }
    }
    if (pt != null) {
        val di = Cycle.deltaPct(t.avgIncome, pt.avgIncome); val de = Cycle.deltaPct(t.avgExpense, pt.avgExpense)
        Card3D(Modifier.fillMaxWidth(), tint = Sky50) {
            Text("সারকথা: আগের কাজের মাসের তুলনায় দিনপ্রতি আয় ${if ((di ?: 0.0) >= 0) "বেড়েছে" else "কমেছে"} ${Fmt.bn(abs(di ?: 0.0).roundToInt())}%, খরচ ${if ((de ?: 0.0) >= 0) "বেড়েছে" else "কমেছে"} ${Fmt.bn(abs(de ?: 0.0).roundToInt())}%।", fontSize = 13.sp, color = Navy, lineHeight = 20.sp)
        }
    }
}

@Composable
private fun RecordsTab(f: FolderE, es: List<EntryE>, today: LocalDate) {
    val weeks = Cycle.blocks(f, es, today, 7)
    Text("ফোল্ডারের শুরুর দিন থেকে প্রতি ৭ দিনে একটা করে সপ্তাহের রিপোর্ট নিজে তৈরি ও সংরক্ষিত হয়।", fontSize = 12.sp, color = Slate)
    if (weeks.size >= 2) Card3D(Modifier.fillMaxWidth()) {
        Text("সাপ্তাহিক লাভের ধারা", fontWeight = FontWeight.Bold, color = Navy, fontSize = 14.sp)
        TwoLineChart(weeks.map { it.t.profit }, emptyList(), weeks.map { Fmt.bn(it.no) }, Modifier.fillMaxWidth().height(100.dp).padding(top = 6.dp), Sky600, Sky600, Sky100, Slate, Color.White)
    }
    weeks.reversed().forEachIndexed { idx, w ->
        val prev = weeks.getOrNull(weeks.size - 2 - idx)
        val running = idx == 0 && f.endDay == null
        Card3D(Modifier.fillMaxWidth(), border = if (running) Sky400 else Sky100, pad = 12.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("সপ্তাহ ${Fmt.bn(w.no)} · ${Fmt.range(w.from, w.to)}", fontWeight = FontWeight.Bold, color = Navy, fontSize = 14.sp)
                    Text(if (running) "চলমান" else if (w.partial) "আংশিক · সংরক্ষিত" else "সংরক্ষিত", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (running) Sky700 else Slate)
                    Text("আয় ${Fmt.taka(w.t.income)} · খরচ ${Fmt.taka(w.t.expense)}", fontSize = 12.sp, color = Slate)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("নিট লাভ", fontSize = 12.sp, color = Slate)
                    Text(Fmt.taka(w.t.profit), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = if (w.t.profit >= 0) Green else Rose)
                    if (prev != null) DeltaChip(Cycle.deltaPct(w.t.profit, prev.t.profit))
                }
            }
        }
    }
}
