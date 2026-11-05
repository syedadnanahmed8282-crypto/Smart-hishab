package com.smarthisab.app.ui

import androidx.compose.animation.core.*
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import kotlinx.coroutines.delay
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

    // ---- আবহাওয়া (সূর্যের ওপর তাপমাত্রা) ----
    val ctx = LocalContext.current
    val weather by vm.weather.collectAsState()
    val wLoading by vm.weatherLoading.collectAsState()
    val locPerms = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    fun hasLoc() = locPerms.any { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.refreshWeather(true) }
    LaunchedEffect(Unit) { if (!hasLoc()) permLauncher.launch(locPerms) else vm.refreshWeather(false) }
    LaunchedEffect(Unit) { while (true) { delay(15 * 60_000L); vm.refreshWeather(false) } }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val ob = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) vm.refreshWeather(false) }
        owner.lifecycle.addObserver(ob)
        onDispose { owner.lifecycle.removeObserver(ob) }
    }
    Box(Modifier.fillMaxSize().background(Sky50)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth()) {
                HeroBackdrop(Modifier.fillMaxWidth().height(340.dp))
                Column(Modifier.fillMaxWidth()) {
                    HeroText(viewer, today)
                    // ফোল্ডার বাছাই: লক্ষ্য কার্ডের ঠিক বাম কোণার ওপরে, কার্ডের অংশ নয়
                    Row(Modifier.padding(start = 20.dp, top = 12.dp, end = 20.dp)) { FolderPill(vm, nav, folder) }
                    if (folder != null) GoalCard(folder, es, today, Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 14.dp))
                }
                WeatherBadge(
                    weather, wLoading, vm.wx.hasKey,
                    onClick = { if (!hasLoc()) permLauncher.launch(locPerms) else vm.refreshWeather(true) },
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 48.dp, end = 18.dp),
                )
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
private fun HeroText(viewer: Boolean, today: LocalDate) {
    val hour = LocalTime.now().hour
    val greet = when { hour < 12 -> "শুভ সকাল"; hour < 16 -> "শুভ দুপুর"; hour < 19 -> "শুভ বিকাল"; else -> "শুভ সন্ধ্যা" }
    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 24.dp, end = 20.dp, top = 12.dp)) {
        Text(greet, fontSize = 14.sp, color = Color(0xFF0C4A6E))
        Text("স্মার্ট হিসাব", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Navy)
        Text("${Fmt.weekdayFull(today)}, ${Fmt.dateY(today)}", fontSize = 13.sp, color = Color(0xFF0C4A6E))
        if (viewer) Row(Modifier.padding(top = 6.dp).clip(CircleShape).background(Color(0xFFEEF2FF)).padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Ico(Ic.eye, size = 14.dp, tint = Color(0xFF3730A3)); Text(" ভিউয়ার মোড · শুধু দেখা", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3730A3))
        }
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
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 86.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DualRing(incP, expP, warn, 66.dp) { Text("৳", color = Color.White.copy(alpha = .55f), fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                // মাসিক লক্ষ্য ও সীমা: কম লেখা, এক লাইনে মান
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    GoalItem(Color.White, "আয়", Fmt.taka(cur.t.income), "লক্ষ্য", goal, incP, false)
                    Spacer(Modifier.height(5.dp))
                    GoalItem(warn, "খরচ", Fmt.taka(cur.t.expense), "সীমা", limit, expP, expP >= .8f)
                }
                // চক্রের গড়/দিন: মাল, আয়, খরচ, বাকি (বাকি = আয় − খরচ), এক এক লাইনে, বর্ডার ছাড়া
                Column(Modifier.width(120.dp), verticalArrangement = Arrangement.Center) {
                    Text("চক্রের গড়/দিন", color = Color.White, fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    AvgRow("মাল", "${Fmt.bn(tot.avgPieces.roundToInt())} পিস", Sky300)
                    AvgRow("আয়", Fmt.taka(tot.avgIncome), Color.White)
                    AvgRow("খরচ", Fmt.taka(tot.avgExpense), Pink)
                    AvgRow("বাকি", Fmt.taka(tot.avgIncome - tot.avgExpense), Color(0xFF6EE7B7))
                }
            }
            Spacer(Modifier.height(6.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = .10f), Color.White.copy(alpha = .04f))))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("শেষ ৭ দিন", color = Color.White, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { LegendLine(Color.White, "আয়"); LegendLine(Pink, "খরচ") }
                }
                TwoLineChart(
                    days.map { it.income }, days.map { it.expense },
                    days.map { if (it.date == today) "আজ" else Fmt.bn(it.date.dayOfMonth) },
                    Modifier.fillMaxWidth().height(68.dp), area = true, glow = true, todayLabel = Fmt.taka(days.last().income),
                )
            }
        }
        if (f.closed) SealStamp(tot, Cycle.start(f), Cycle.end(f, today), Modifier.align(Alignment.Center).padding(top = 40.dp).widthIn(max = 290.dp))
    }
}

@Composable
private fun GoalItem(dot: Color, label: String, value: String, limitLabel: String, limit: Double?, p: Float, warn: Boolean) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
            Text(label, Modifier.padding(start = 5.dp), fontSize = 12.sp, lineHeight = 18.sp, color = Sky100, maxLines = 1, softWrap = false)
            if (warn) Ico(Ic.warn, Modifier.padding(start = 3.dp), size = 12.dp, tint = dot, sw = 2.4f)
            FitText(value, Modifier.weight(1f).padding(start = 6.dp), maxSize = 14.sp, minSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold, lineHeight = 18.sp)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FitText(if (limit != null) "$limitLabel ${Fmt.taka(limit)}" else "$limitLabel ঠিক করা নেই", Modifier.weight(1f), maxSize = 12.sp, minSize = 9.sp, color = Sky200, lineHeight = 17.sp)
            if (limit != null) Text(pct(p), Modifier.padding(start = 4.dp), fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold, color = if (warn) dot else Color.White, maxLines = 1, softWrap = false)
        }
    }
}

@Composable
private fun AvgRow(label: String, value: String, dot: Color) {
    Row(Modifier.fillMaxWidth().heightIn(min = 17.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
        Text(label, Modifier.padding(start = 4.dp), fontSize = 12.sp, lineHeight = 17.sp, color = Sky100, maxLines = 1, softWrap = false)
        FitText(value, Modifier.weight(1f).padding(start = 4.dp), maxSize = 13.sp, minSize = 8.sp, color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, lineHeight = 17.sp)
    }
}

@Composable
private fun LegendLine(c: Color, t: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 14.dp, height = 3.dp).clip(CircleShape).background(c))
        Text(t, Modifier.padding(start = 4.dp), fontSize = 12.sp, lineHeight = 16.sp, color = Sky100)
    }
}

@Composable
private fun HomeBody(vm: AppVm, nav: NavHostController, f: FolderE, es: List<EntryE>, viewer: Boolean, today: LocalDate) {
    val t = Cycle.totals(f, es, today)
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StatsRow(t)
        if (f.closed) Banner("নতুন এন্ট্রি বা মোছা যাবে না", "এই ফোল্ডার বন্ধ", Color(0xFFFFF1F2), Color(0xFFFECDD3), Rose) {
            if (!viewer) TextButton({ vm.reopenFolder(f.id) }) { Text("আবার চালু করুন", color = Rose, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("দৈনিক হিসাব", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Navy, maxLines = 1, softWrap = false)
                Text("কাজ ${Fmt.bn(t.work)} দিন", Modifier.clip(CircleShape).background(Color(0xFFD1FAE5)).padding(horizontal = 9.dp, vertical = 3.dp), color = Green, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                Text("বন্ধ ${Fmt.bn(t.off)} দিন", Modifier.clip(CircleShape).background(Color(0xFFFFE4E6)).padding(horizontal = 9.dp, vertical = 3.dp), color = Rose, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
            }
            TextButton({ nav.goTab("history") }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("সব দেখুন", fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false) }
        }
        if (es.isEmpty()) Text("এখনও কোনো হিসাব নেই। নিচের + বাটনে চাপুন।", color = Slate, fontSize = 14.sp)
        es.take(3).forEachIndexed { i, e -> EntryRow(e, null, Modifier.riseIn(2 + i)) }
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
            Text(Fmt.monthShort(d), color = Color.White, fontSize = 12.sp, lineHeight = 13.sp, maxLines = 1, softWrap = false)
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
