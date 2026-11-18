package com.smarthisab.app.ui

import androidx.compose.animation.core.*
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarthisab.app.data.EntryE
import com.smarthisab.app.domain.*
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.roundToInt

fun modeIcon(m: Mode): String = when (m) { Mode.MAIN -> Ic.box; Mode.ALT -> Ic.wallet; Mode.PREMIUM -> Ic.swap }
fun modeSub(m: Mode): String = when (m) {
    Mode.MAIN -> "মাল × রেট = আয়, সাথে খরচ ও নিট বাকি"
    Mode.ALT -> "শুধু খরচ লিখুন, মাল বা মজুরি লাগে না"
    Mode.PREMIUM -> "আয় ও খরচ আলাদা, খাত অনুযায়ী হিসাব"
}
private fun modeTint(m: Mode): Pair<Color, Color> = when (m) {
    Mode.MAIN -> Sky100 to Sky700
    Mode.ALT -> Color(0xFFFFF1F2) to Rose
    Mode.PREMIUM -> Color(0xFFECFDF5) to Green
}

/** একটা ধরনের সারি (রেডিও সহ) — সেটিংস ও হোমের শিটে একই */
@Composable
fun ModeRow(mode: Mode, selected: Boolean, dim: Boolean = false, onClick: () -> Unit) {
    val (bg, fg) = modeTint(mode)
    Row(
        Modifier.fillMaxWidth().alpha(if (dim) .45f else 1f).background(if (selected) Color(0xFFEAF6FE) else Color.Transparent).bounceClick(true, onClick).heightIn(min = 60.dp).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(bg), contentAlignment = Alignment.Center) { Ico(modeIcon(mode), size = 18.dp, tint = fg) }
        Column(Modifier.weight(1f)) {
            Text(mode.label, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = Navy)
            Text(modeSub(mode), fontSize = 12.sp, lineHeight = 16.sp, color = Slate)
        }
        if (selected) Box(Modifier.size(24.dp).clip(CircleShape).background(Sky800), contentAlignment = Alignment.Center) { Ico(Ic.check, size = 14.dp, tint = Color.White, sw = 3f) }
        else Box(Modifier.size(24.dp).border(2.dp, Color(0xFFCBD5E1), CircleShape))
    }
}

/** মোট কার্ড: ধরন অনুযায়ী (মালের হিসাবে আগের চারটাই) */
@Composable
fun ModeStats(t: Totals, mode: Mode, es: List<EntryE>) {
    when (mode) {
        Mode.MAIN -> StatsRow(t)
        Mode.ALT -> {
            val maxDay = Cycle.dayExpenses(es).maxOfOrNull { it.second } ?: 0.0
            val top = Cycle.categoryTotals(es, "expense").firstOrNull()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MiniStat("মোট খরচ", Fmt.taka(t.expense), Rose, Modifier.weight(1f))
                MiniStat("গড়/দিন", Fmt.taka(t.avgExpense), Sky700, Modifier.weight(1f))
                MiniStat("সর্বোচ্চ দিন", Fmt.taka(maxDay), Amber, Modifier.weight(1f))
                MiniStat("বড় খাত", if (top == null || t.expense <= 0) "—" else "${top.first} ${Fmt.bn((top.second / t.expense * 100).roundToInt())}%", Green, Modifier.weight(1f))
            }
        }
        Mode.PREMIUM -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MiniStat("মোট আয়", Fmt.taka(t.income), Green, Modifier.weight(1f))
            MiniStat("মোট খরচ", Fmt.taka(t.expense), Rose, Modifier.weight(1f))
            MiniStat("মোট বাকি", Fmt.taka(t.profit), Sky700, Modifier.weight(1f))
            MiniStat("এন্ট্রির দিন", Fmt.bn(t.days), Amber, Modifier.weight(1f))
        }
    }
}

/** দৈনিক খরচের বার চার্ট: শেষ ৭ এন্ট্রির দিন, গড় রেখা সহ (হোম কার্ডের নীল জমিতে) */
@Composable
fun DayBars(values: List<Double>, labels: List<String>, avg: Double, modifier: Modifier = Modifier) {
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { go = true }
    val p by animateFloatAsState(if (go) 1f else 0f, tween(1100, 300, FastOutSlowInEasing), label = "db")
    val maxV = (values.maxOrNull() ?: 1.0).coerceAtLeast(avg).coerceAtLeast(1.0)
    Column(modifier) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            Canvas(Modifier.fillMaxSize()) {
                val n = values.size
                if (n == 0) return@Canvas
                val top = 8.dp.toPx(); val h = size.height - top
                val slot = size.width / n; val bw = 16.dp.toPx()
                drawLine(Color.White.copy(alpha = .3f), Offset(0f, top + h), Offset(size.width, top + h), 1.dp.toPx())
                values.forEachIndexed { i, v ->
                    val bh = (v / maxV).toFloat() * (h - 4.dp.toPx()) * p
                    drawRoundRect(if (i == n - 1) Color.White else Pink, Offset((i + .5f) * slot - bw / 2, top + h - bh), Size(bw, max(bh, 1f)), CornerRadius(5.dp.toPx()))
                }
                val ay = top + h - (avg / maxV).toFloat() * (h - 4.dp.toPx())
                drawLine(Color(0xFFFDE68A), Offset(0f, ay), Offset(size.width, ay), 1.2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
            }
            Text("গড় ${Fmt.taka(avg)}", Modifier.align(Alignment.TopEnd), fontSize = 12.sp, lineHeight = 16.sp, color = Color(0xFFFDE68A), maxLines = 1, softWrap = false)
        }
        Row(Modifier.fillMaxWidth()) {
            labels.forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 12.sp, lineHeight = 16.sp, color = Sky100) }
        }
    }
}

@Composable
private fun DateChip(d: LocalDate, grad: List<Color>) {
    Column(
        Modifier.size(42.dp).shadow(5.dp, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(grad)),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Text(Fmt.bn(d.dayOfMonth), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 17.sp)
        Text(Fmt.monthShort(d), color = Color.White, fontSize = 12.sp, lineHeight = 13.sp, maxLines = 1, softWrap = false)
    }
}

/** হোমের তালিকা: দিন অনুযায়ী সারাংশ (দৈনিক খরচ ও আয়-ব্যয়) */
@Composable
fun DayRowItem(r: DayRow, mode: Mode, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier.fillMaxWidth().shadow(6.dp, shape, ambientColor = Sky500, spotColor = Sky500).background(Color.White, shape).border(1.dp, Sky100, shape).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DateChip(r.date, listOf(Sky400, Sky800))
        Column(Modifier.weight(1f)) {
            FitText(
                if (mode == Mode.ALT) (r.cats.takeIf { it.isNotEmpty() }?.joinToString(" · ") ?: "খরচ") else "আয় ${Fmt.taka(r.income)} · খরচ ${Fmt.taka(r.expense)}",
                Modifier.fillMaxWidth(), maxSize = 15.sp, minSize = 10.sp, color = Navy, fontWeight = FontWeight.Bold, lineHeight = 20.sp,
            )
            Text("${Fmt.bn(r.count)}টা এন্ট্রি", fontSize = 12.sp, color = Slate)
        }
        if (mode == Mode.ALT) Column(horizontalAlignment = Alignment.End) {
            Text(Fmt.taka(r.expense), fontWeight = FontWeight.Bold, color = Rose, fontSize = 15.sp)
            Text("খরচ", fontSize = 12.sp, color = Slate)
        } else Column(horizontalAlignment = Alignment.End) {
            Text((if (r.net >= 0) "+" else "−") + Fmt.taka(kotlin.math.abs(r.net)), fontWeight = FontWeight.Bold, color = if (r.net >= 0) Green else Rose, fontSize = 15.sp)
            Text("বাকি", fontSize = 12.sp, color = Slate)
        }
    }
}

/** ইতিহাসের তালিকা: প্রতিটা এন্ট্রি আলাদা (দৈনিক খরচ ও আয়-ব্যয়) */
@Composable
fun MoneyRow(e: EntryE, onMenu: (() -> Unit)?, modifier: Modifier = Modifier) {
    val d = LocalDate.ofEpochDay(e.day)
    val income = e.kind == "income"
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier.fillMaxWidth().shadow(6.dp, shape, ambientColor = Sky500, spotColor = Sky500).background(Color.White, shape).border(1.dp, Sky100, shape)
            .padding(start = 14.dp, end = if (onMenu == null) 14.dp else 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DateChip(d, if (income) listOf(Color(0xFF34D399), Color(0xFF047857)) else listOf(Sky400, Sky800))
        Column(Modifier.weight(1f)) {
            Text(e.category ?: if (income) "আয়" else "খরচ", fontWeight = FontWeight.Bold, color = Navy, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(e.note?.takeIf { it.isNotBlank() } ?: if (income) "আয়" else "খরচ", fontSize = 12.sp, color = Slate, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text((if (income) "+" else "−") + Fmt.taka(e.amount), fontWeight = FontWeight.Bold, color = if (income) Green else Rose, fontSize = 15.sp, maxLines = 1, softWrap = false)
        if (onMenu != null) IconButton(onMenu, Modifier.size(32.dp)) { Ico(Ic.more, size = 22.dp, tint = Slate, sw = 3.4f) }
    }
}

/** নিজের খাত যোগ বা মুছুন। মুছলে আগের এন্ট্রির খাতের নাম বদলায় না, শুধু বাছাইয়ের তালিকা থেকে সরে। */
@Composable
fun CatsDialog(vm: AppVm, mode: Mode, expCustom: List<String>, incCustom: List<String>, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("expense") }
    val defaults = if (kind == "income") Cats.income else Cats.expense
    val custom = if (kind == "income") incCustom else expCustom
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("খাত পরিচালনা", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (mode == Mode.PREMIUM) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip("খরচের খাত", kind == "expense") { kind = "expense" }
                    Chip("আয়ের উৎস", kind == "income") { kind = "income" }
                }
                Text("ডিফল্ট: ${defaults.joinToString(", ")}", fontSize = 12.sp, lineHeight = 17.sp, color = Slate)
                custom.forEach { c ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(c, Modifier.weight(1f), fontSize = 15.sp, color = Navy)
                        TextButton({ vm.removeCat(kind, c) }) { Text("মুছুন", color = Rose) }
                    }
                }
                if (custom.isEmpty()) Text("এখনও নিজের কোনো খাত যোগ করা হয়নি", fontSize = 12.sp, color = Slate)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Field("নতুন খাতের নাম", name, { name = it.take(40) }, Modifier.weight(1f))
                    TextButton({ vm.addCat(kind, name); name = "" }, enabled = name.isNotBlank()) { Text("যোগ", fontWeight = FontWeight.Bold) }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("ঠিক আছে", fontWeight = FontWeight.Bold) } },
    )
}

/** খাতের নাম থেকে আইকন (নিজের যোগ করা খাতে ট্যাগ) */
fun categoryIcon(name: String?): String = when (name) {
    "নাস্তা" -> Ic.cup; "ভাত" -> Ic.bowl; "গাড়ি ভাড়া" -> Ic.bus; "বিক্রি" -> Ic.cart; "মজুরি" -> Ic.cash; "বেতন" -> Ic.wallet
    Cats.OTHER, "অন্যান্য আয়" -> Ic.dots; else -> Ic.tag
}

/** ইতিহাসে আয়-ব্যয়ের দিন-কার্ড: ওপরে দিনের মোট, খুললে প্রতিটা আয়ের উৎস ও খরচের খাত আলাদা সারিতে */
@Composable
fun PremDayCard(mode: Mode, date: LocalDate, dayEntries: List<EntryE>, shown: List<EntryE>, expanded: Boolean, locked: Boolean, onToggle: () -> Unit, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    val inc = dayEntries.sumOf { incomeOf(it) }
    val exp = dayEntries.sumOf { expenseOf(it) }
    val net = inc - exp
    val nInc = dayEntries.count { it.kind == "income" }
    val shape = RoundedCornerShape(20.dp)
    Column(modifier.fillMaxWidth().shadow(6.dp, shape, ambientColor = Sky500, spotColor = Sky500).background(Color.White, shape).border(1.dp, Sky100, shape).clip(shape).animateContentSize().padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth().bounceClick(true, onToggle), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DateChip(date, listOf(Sky400, Sky800))
            Column(Modifier.weight(1f)) {
                if (mode == Mode.ALT) {
                    FitText("খরচ ${Fmt.taka(exp)}", Modifier.fillMaxWidth(), maxSize = 16.sp, minSize = 10.sp, color = Rose, fontWeight = FontWeight.ExtraBold, lineHeight = 22.sp)
                    Text("${Fmt.bn(dayEntries.size)}টা খরচ", fontSize = 12.sp, lineHeight = 16.sp, color = Slate, maxLines = 1)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Ico(Ic.up, size = 14.dp, tint = Green, sw = 2.6f)
                        FitText("আয় ${Fmt.taka(inc)}", Modifier.weight(1f).padding(start = 6.dp), maxSize = 14.sp, minSize = 9.sp, color = Green, fontWeight = FontWeight.Bold, lineHeight = 20.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Ico(Ic.dn, size = 14.dp, tint = Rose, sw = 2.6f)
                        FitText("খরচ ${Fmt.taka(exp)}", Modifier.weight(1f).padding(start = 6.dp), maxSize = 14.sp, minSize = 9.sp, color = Rose, fontWeight = FontWeight.Bold, lineHeight = 20.sp)
                    }
                    Text("${Fmt.bn(dayEntries.size)}টা এন্ট্রি · ${Fmt.bn(nInc)} আয়, ${Fmt.bn(dayEntries.size - nInc)} খরচ", fontSize = 12.sp, lineHeight = 16.sp, color = Slate, maxLines = 1)
                }
            }
            if (mode != Mode.ALT) Column(horizontalAlignment = Alignment.End) {
                Text((if (net >= 0) "+" else "−") + Fmt.taka(kotlin.math.abs(net)), fontWeight = FontWeight.ExtraBold, color = if (net >= 0) Green else Rose, fontSize = 15.sp, maxLines = 1, softWrap = false)
                Text("বাকি", fontSize = 12.sp, lineHeight = 16.sp, color = Slate)
            }
            Ico(Ic.chev, Modifier.graphicsLayer { rotationZ = if (expanded) 180f else 0f }, size = 18.dp, tint = Slate, sw = 2.4f)
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = Color(0xFFEEF3F8))
            val incRows = shown.filter { it.kind == "income" }
            val expRows = shown.filter { it.kind != "income" }
            incRows.forEach { PremLine(it) }
            if (incRows.isNotEmpty() && expRows.isNotEmpty()) HorizontalDivider(Modifier.padding(vertical = 2.dp), color = Color(0xFFEEF3F8))
            expRows.forEach { PremLine(it) }
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp).height(40.dp).clip(RoundedCornerShape(14.dp)).background(Sky100).bounceClick(true, onEdit),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Ico(Ic.edit, size = 16.dp, tint = Sky800, sw = 2.4f)
                Text(" এই দিনের হিসাব সম্পাদনা", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (locked) Slate else Sky800)
            }
        }
    }
}

@Composable
private fun PremLine(e: EntryE) {
    val income = e.kind == "income"
    val col = if (income) Green else Rose
    Row(Modifier.fillMaxWidth().heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).background(if (income) Color(0xFFD1FAE5) else Color(0xFFFFE4E6)), contentAlignment = Alignment.Center) { Ico(categoryIcon(e.category), size = 16.dp, tint = col) }
        Column(Modifier.weight(1f)) {
            Text(e.category ?: if (income) "আয়" else "খরচ", fontSize = 14.sp, lineHeight = 20.sp, color = Navy, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!e.note.isNullOrBlank()) Text(e.note, fontSize = 12.sp, lineHeight = 16.sp, color = Slate, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text((if (income) "+" else "−") + Fmt.taka(e.amount), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = col, maxLines = 1, softWrap = false)
    }
}
