package com.smarthisab.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.smarthisab.app.data.EntryE
import com.smarthisab.app.data.FolderE
import com.smarthisab.app.domain.*
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt

private fun pct(x: Float) = Fmt.bn((x * 100).roundToInt()) + "%"

@Composable
fun HomeScreen(vm: AppVm, nav: NavHostController) {
    val f by vm.current.collectAsState()
    val es by vm.currentEntries.collectAsState()
    val viewer by vm.viewer.collectAsState()
    val today = remember { LocalDate.now() }
    val folder = f
    Box(Modifier.fillMaxSize().background(Sky50)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth()) {
                HeroBackdrop(Modifier.fillMaxWidth().height(340.dp))
                Column(Modifier.fillMaxWidth()) {
                    HeroText(vm, nav, folder, viewer, today)
                    if (folder != null) GoalCard(folder, es, today, Modifier.padding(horizontal = 20.dp, vertical = 14.dp))
                }
            }
            if (folder == null) EmptyHome(nav) else HomeBody(vm, nav, folder, es, viewer, today)
            Spacer(Modifier.height(96.dp))
        }
        if (folder != null) {
            val locked = folder.closed || viewer
            Fab(locked, Modifier.align(Alignment.BottomEnd).padding(24.dp)) {
                if (!locked) nav.navigate("entry") else vm.say(if (viewer) "ভিউয়ার মোডে নতুন এন্ট্রি বন্ধ" else "ফোল্ডার সমাপ্ত, আগে আবার চালু করুন")
            }
        }
    }
}

@Composable
private fun HeroText(vm: AppVm, nav: NavHostController, f: FolderE?, viewer: Boolean, today: LocalDate) {
    val hour = LocalTime.now().hour
    val greet = when { hour < 12 -> "শুভ সকাল"; hour < 16 -> "শুভ দুপুর"; hour < 19 -> "শুভ বিকাল"; else -> "শুভ সন্ধ্যা" }
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 24.dp, end = 20.dp, top = 12.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(greet, fontSize = 14.sp, color = Color(0xFF0C4A6E))
            Text("স্মার্ট হিসাব", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Navy)
            Text("${Fmt.weekdayFull(today)}, ${Fmt.dateY(today)}", fontSize = 13.sp, color = Color(0xFF0C4A6E))
            if (f != null) {
                val d = Cycle.dayNo(f, today)
                val tag = if (f.closed) "চক্র সমাপ্ত · ${Fmt.range(Cycle.start(f), Cycle.end(f, today))} · ${Fmt.bn(Cycle.totalDays(f, today))} দিন"
                else "কাজের মাস ${Fmt.bn((d - 1) / 30 + 1)} · সপ্তাহ ${Fmt.bn((d - 1) / 7 + 1)} · দিন ${Fmt.bn(d)}"
                Text(tag, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (f.closed) Color(0xFF9F1239) else Sky800, modifier = Modifier.padding(top = 2.dp))
            }
            if (viewer) Row(Modifier.padding(top = 6.dp).clip(CircleShape).background(Color(0xFFEEF2FF)).padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Ico(Ic.eye, size = 14.dp, tint = Color(0xFF3730A3)); Text(" ভিউয়ার মোড · শুধু দেখা", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3730A3))
            }
        }
        FolderPill(vm, nav, f)
    }
}

@Composable
private fun GoalCard(f: FolderE, es: List<EntryE>, today: LocalDate, modifier: Modifier) {
    val tot = Cycle.totals(f, es, today)
    val cur = Cycle.blocks(f, es, today, 30).last()
    val goal = f.incomeGoal?.takeIf { it > 0 }
    val limit = f.expenseLimit?.takeIf { it > 0 }
    val incP = if (goal != null) (cur.t.income / goal).toFloat() else 0f
    val expP = if (limit != null) (cur.t.expense / limit).toFloat() else 0f
    val warn = when { expP >= 1f -> Red300; expP >= .8f -> Yellow; else -> Pink }
    val days = Cycle.lastDays(f, es, today)
    BlueCard(modifier.fillMaxWidth(), delay = 1) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                DualRing(incP, expP, warn, 80.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(pct(incP), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(pct(expP), color = warn, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
                Column(Modifier.weight(1f)) {
                    MetricRow("মাসিক আয়ের লক্ষ্য", Color.White, Color(0xFFE0F2FE), pct(incP), Color.White)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(Fmt.taka(cur.t.income), color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Text(if (goal != null) " / ${Fmt.taka(goal)}" else " (লক্ষ্য ঠিক করা নেই)", color = Sky100, fontSize = 12.sp)
                    }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp), color = Color.White.copy(alpha = .2f))
                    MetricRow("মাসিক খরচের সীমা", warn, warn, pct(expP), warn, warnIcon = expP >= .8f)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(Fmt.taka(cur.t.expense), color = if (expP >= .8f) warn else Color(0xFFFFE4E6), fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Text(if (limit != null) " / ${Fmt.taka(limit)}" else " (সীমা ঠিক করা নেই)", color = warn, fontSize = 12.sp)
                    }
                }
            }
            HorizontalDivider(Modifier.padding(top = 10.dp, bottom = 8.dp), color = Color.White.copy(alpha = .22f))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("চক্রের গড়/দিন", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Legend(Color.White, "আয় ${Fmt.taka(tot.avgIncome)}")
                Legend(Pink, "খরচ ${Fmt.taka(tot.avgExpense)}")
                Text("${Fmt.bn(tot.avgPieces.roundToInt())} পিস", color = Sky100, fontSize = 12.sp)
            }
            TwoLineChart(
                days.map { it.income }, days.map { it.expense },
                days.map { if (it.date == today) "আজ" else Fmt.bn(it.date.dayOfMonth) },
                Modifier.fillMaxWidth().height(100.dp).padding(top = 4.dp),
            )
        }
        if (f.closed) SealStamp(tot, Cycle.start(f), Cycle.end(f, today), Modifier.align(Alignment.Center).padding(top = 40.dp).widthIn(max = 290.dp))
    }
}

@Composable
private fun MetricRow(label: String, dot: Color, labelColor: Color, pct: String, pctColor: Color, warnIcon: Boolean = false) {
    val pulse by rememberInfiniteTransition(label = "warn").animateFloat(1f, 1.3f, infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "w")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        if (warnIcon) Ico(Ic.warn, Modifier.padding(start = 4.dp).graphicsLayer { scaleX = pulse; scaleY = pulse }, size = 13.dp, tint = dot, sw = 2.4f)
        Text(label, Modifier.padding(start = 5.dp).weight(1f), color = labelColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(pct, color = pctColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Legend(c: Color, t: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(c)); Text(t, Modifier.padding(start = 4.dp), color = Sky100, fontSize = 12.sp)
    }
}

@Composable
private fun HomeBody(vm: AppVm, nav: NavHostController, f: FolderE, es: List<EntryE>, viewer: Boolean, today: LocalDate) {
    val t = Cycle.totals(f, es, today)
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MiniStat("মোট মাল", Fmt.bn(t.pieces) + " পিস", Navy, Modifier.weight(1f))
            MiniStat("মোট আয়", Fmt.taka(t.income), Green, Modifier.weight(1f))
            MiniStat("মোট খরচ", Fmt.taka(t.expense), Rose, Modifier.weight(1f))
            MiniStat("মোট বাকি", Fmt.taka(t.profit), Sky700, Modifier.weight(1f))
        }
        if (f.closed) Banner("নতুন এন্ট্রি বা মোছা যাবে না", "এই ফোল্ডার বন্ধ", Color(0xFFFFF1F2), Color(0xFFFECDD3), Rose) {
            if (!viewer) TextButton({ vm.reopenFolder(f.id) }) { Text("আবার চালু করুন", color = Rose, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("দৈনিক হিসাব", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Navy)
            Text("কাজ ${Fmt.bn(t.work)} দিন", Modifier.clip(CircleShape).background(Color(0xFFD1FAE5)).padding(horizontal = 9.dp, vertical = 3.dp), color = Green, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text("বন্ধ ${Fmt.bn(t.off)} দিন", Modifier.clip(CircleShape).background(Color(0xFFFFE4E6)).padding(horizontal = 9.dp, vertical = 3.dp), color = Rose, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            TextButton({ nav.navigate("history") }) { Text("সব দেখুন", fontWeight = FontWeight.SemiBold) }
        }
        if (es.isEmpty()) Text("এখনও কোনো হিসাব নেই। নিচের + বাটনে চাপুন।", color = Slate, fontSize = 14.sp)
        es.take(3).forEachIndexed { i, e -> EntryRow(e, null, Modifier.riseIn(2 + i)) }
    }
}

@Composable
private fun MiniStat(label: String, value: String, color: Color, modifier: Modifier) {
    Card3D(modifier, pad = 9.dp) {
        Text(label, fontSize = 12.sp, color = Slate, maxLines = 1)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = color, maxLines = 1, overflow = TextOverflow.Clip)
    }
}

@Composable
fun EntryRow(e: EntryE, onMenu: (() -> Unit)?, modifier: Modifier = Modifier) {
    val d = LocalDate.ofEpochDay(e.day)
    val inc = incomeOf(e)
    val off = e.quantity == 0
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier.fillMaxWidth().shadow(6.dp, shape, ambientColor = Sky500, spotColor = Sky500).background(if (off) Color(0xFFFFF1F2) else Color.White, shape)
            .border(1.dp, if (off) Color(0xFFFFE4E6) else Sky100, shape).padding(start = 14.dp, end = if (onMenu == null) 14.dp else 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            Modifier.size(42.dp).shadow(5.dp, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(if (off) listOf(Pink, Color(0xFFBE123C)) else listOf(Sky400, Sky800))),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Text(Fmt.bn(d.dayOfMonth), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 17.sp)
            Text(Fmt.weekday(d), color = Color.White, fontSize = 12.sp, lineHeight = 13.sp)
        }
        Column(Modifier.weight(1f)) {
            if (off) {
                Text("বন্ধ · মাল ০", fontWeight = FontWeight.Bold, color = Rose, fontSize = 15.sp)
                Text("কোনো কাজ হয়নি", fontSize = 12.sp, color = Slate)
            } else {
                Text("${Fmt.bn(e.quantity)} পিস", fontWeight = FontWeight.Bold, color = Navy, fontSize = 15.sp)
                Text("আয় ${Fmt.taka(inc)} · লাভ ${Fmt.taka(inc - e.expense)}", fontSize = 12.sp, color = Slate, maxLines = 1)
            }
        }
        if (e.expense > 0 || !off) Column(horizontalAlignment = Alignment.End) {
            Text(Fmt.taka(e.expense), fontWeight = FontWeight.Bold, color = Rose, fontSize = 15.sp)
            Text("খরচ", fontSize = 12.sp, color = Slate)
        } else Text("বন্ধ", fontWeight = FontWeight.Bold, color = Rose, fontSize = 13.sp)
        if (onMenu != null) IconButton(onMenu, Modifier.size(32.dp)) { Ico(Ic.more, size = 22.dp, tint = Slate, sw = 3.4f) }
    }
}

@Composable
private fun EmptyHome(nav: NavHostController) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("প্রথমে একটা ফোল্ডার (কাজের চক্র) বানান", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Navy)
        Text("কাজ শুরুর তারিখ থেকে সব সপ্তাহ ও মাসের হিসাব নিজে মিলে যাবে।", color = Slate, fontSize = 14.sp)
        PrimaryButton("নতুন ফোল্ডার তৈরি করুন", Modifier.fillMaxWidth()) { nav.navigate("newfolder") }
    }
}

@Composable
private fun Fab(locked: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val t = rememberInfiniteTransition(label = "fab")
    val bob by t.animateFloat(0f, -6f, infiniteRepeatable(tween(1500), RepeatMode.Reverse), label = "bob")
    val pulse by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "pulse")
    val shape = RoundedCornerShape(22.dp)
    Box(modifier.offset(y = bob.dp).size(60.dp)) {
        if (!locked) Box(Modifier.fillMaxSize().graphicsLayer { val k = 1f + .7f * pulse; scaleX = k; scaleY = k; alpha = .5f * (1f - pulse) }.background(Sky400, shape))
        Box(
            Modifier.fillMaxSize().bounceClick(true, onClick).shadow(14.dp, shape, ambientColor = Sky800, spotColor = Sky800).clip(shape)
                .background(if (locked) Brush.linearGradient(listOf(Color(0xFFE2E8F0), Color(0xFF94A3B8))) else Brush.linearGradient(listOf(Sky400, Sky600))).glossy(22.dp),
            contentAlignment = Alignment.Center,
        ) { Ico(if (locked) Ic.lock else Ic.plus, size = 28.dp, tint = if (locked) Color(0xFF334155) else Color.White, sw = 2.8f) }
    }
}
