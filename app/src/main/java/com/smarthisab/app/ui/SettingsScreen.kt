package com.smarthisab.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import com.smarthisab.app.data.AuthState
import com.smarthisab.app.data.FolderE
import com.smarthisab.app.domain.Fmt
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val Line = Color(0xFFEEF3F8)
private val Gray = Color(0xFF64748B)
private val RowInk = Color(0xFF0F172A)

@Composable
fun SettingsScreen(vm: AppVm, nav: NavHostController) {
    val f by vm.current.collectAsState()
    val viewer by vm.viewer.collectAsState()
    val auth by vm.auth.collectAsState()
    val syncing by vm.syncing.collectAsState()
    val last by vm.lastSync.collectAsState()
    val weather by vm.weather.collectAsState()
    val wLoading by vm.weatherLoading.collectAsState()
    var pinSet by remember { mutableStateOf(false) }
    var pinExit by remember { mutableStateOf(false) }
    var logoutAsk by remember { mutableStateOf(false) }
    var deleteAsk by remember { mutableStateOf(false) }
    var showWx by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<String?>(null) }
    val signedIn = auth == AuthState.In
    val folder = f

    val ctx = LocalContext.current
    val locPerms = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    fun hasLoc() = locPerms.any { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }
    val liveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.refreshWeather(true, true) }

    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFDCEFFB), Color(0xFFF5F9FC)), endY = 520f))) {
        Column(Modifier.statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 10.dp)) {
            Text("অ্যাপ ও ডাটা", fontSize = 13.sp, lineHeight = 18.sp, color = Color(0xFF475569))
            Text("সেটিংস", fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, color = Navy)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 6.dp)) {
            // ১) প্রোফাইল
            Group(0) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(BtnBrush), contentAlignment = Alignment.Center) { Ico(Ic.user, size = 22.dp, tint = Color.White) }
                    Column(Modifier.weight(1f)) {
                        Text(if (signedIn) (vm.repo.userName() ?: "আপনার অ্যাকাউন্ট") else "লগইন করা নেই", fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp, color = RowInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (signedIn) (vm.repo.email() ?: "") else "ডাটা শুধু এই ফোনে আছে", fontSize = 13.sp, lineHeight = 18.sp, color = Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (signedIn) {
                        val a by rememberInfiniteTransition(label = "dot").animateFloat(1f, .4f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "d")
                        Row(Modifier.clip(CircleShape).background(Color(0xFFECFDF5)).padding(horizontal = 9.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(6.dp).alpha(a).clip(CircleShape).background(Color(0xFF10B981)))
                            Text(" সিঙ্ক চালু", fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, color = Green, maxLines = 1, softWrap = false)
                        }
                    } else Text("লগইন", Modifier.bounceClick(true) { vm.logout() }.clip(CircleShape).background(Sky100).padding(horizontal = 12.dp, vertical = 5.dp), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Sky800)
                }
            }
            // ২) আবহাওয়া
            Label("আবহাওয়া")
            Group(1) {
                val sub = weather?.let { w ->
                    buildString {
                        append(w.text)
                        w.humidity?.let { append(" · আর্দ্রতা ${Fmt.bn(it)}%") }
                        w.windKph?.let { append(" · বাতাস ${Fmt.bn(it.roundToInt())} কিমি/ঘ") }
                    }
                } ?: "বিস্তারিত দেখুন"
                SRow(Ic.csun, Color(0xFFFEF3C7), Amber, "আবহাওয়া", sub, weather?.let { "${Fmt.bn(it.tempC.roundToInt())}°C" }, divider = false, valueBold = true) { showWx = true }
            }
            // ৩) ভিউয়ার মোড
            Label("নিরাপত্তা")
            Group(2) {
                SRow(Ic.eye, Color(0xFFEEF2FF), Indigo, "ভিউয়ার মোড", if (viewer) "চালু · শুধু দেখা যাচ্ছে, সিঙ্ক চলছে" else "শুধু দেখা যাবে, কিছু বদলানো যাবে না", chevron = false, divider = false,
                    trailing = { Switch(viewer, { if (viewer) pinExit = true else pinSet = true }, colors = SwitchDefaults.colors(checkedTrackColor = Indigo)) })
            }
            // ৪) হিসাব (লক্ষ্য, সীমা, রেট)
            Label("হিসাব")
            Group(3) {
                if (folder == null) SRow(Ic.folder, Sky100, Sky700, "ফোল্ডার তৈরি করুন", "লক্ষ্য ও সীমা ঠিক করতে আগে একটা ফোল্ডার লাগবে", divider = false) { nav.navigate("newfolder") }
                else {
                    val lock = { vm.say("ভিউয়ার মোডে বদলানো যাবে না") }
                    SRow(Ic.target, Sky100, Sky700, "মাসিক আয়ের লক্ষ্য", null, folder.incomeGoal?.let { Fmt.taka(it) } ?: "ঠিক করুন", divider = false) { if (viewer) lock() else edit = "goal" }
                    SRow(Ic.gauge, Color(0xFFFFF1F2), Rose, "মাসিক খরচের সীমা", null, folder.expenseLimit?.let { Fmt.taka(it) } ?: "ঠিক করুন") { if (viewer) lock() else edit = "limit" }
                    SRow(Ic.coin, Color(0xFFECFDF5), Green, "মজুরি রেট", "প্রতি ১০০ পিস, নতুন এন্ট্রির জন্য", Fmt.taka(folder.wageRate)) { if (viewer) lock() else edit = "wage" }
                    SRow(Ic.box, Color(0xFFFEF3C7), Amber, "দৈনিক টার্গেট", null, folder.dailyTarget?.let { "${Fmt.bn(it)} পিস" } ?: "ঠিক করুন") { if (viewer) lock() else edit = "target" }
                }
            }
            // ৫) ক্লাউড
            Label("ক্লাউড")
            Group(4) {
                val t = last?.toLongOrNull()?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM HH:mm")) }
                val spin by rememberInfiniteTransition(label = "sync").animateFloat(0f, 360f, infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "s")
                SRow(Ic.cloud, Sky100, Sky700, "ক্লাউড সিঙ্ক", if (signedIn) "শেষ সিঙ্ক ${t?.let { Fmt.bn(it) } ?: "এখনও হয়নি"}${if (viewer) " · ভিউয়ার মোডেও চলছে" else ""}" else "লগইন করলে সব হিসাব ক্লাউডে থাকবে",
                    chevron = false, divider = false, trailing = {
                        Row(Modifier.bounceClick(!syncing) { vm.sync(true) }.clip(CircleShape).background(Sky100).padding(horizontal = 11.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Ico(Ic.refresh, Modifier.graphicsLayer { rotationZ = if (syncing) spin else 0f }, size = 14.dp, tint = Sky800, sw = 2.4f)
                            Text(" সিঙ্ক", fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, color = Sky800, maxLines = 1, softWrap = false)
                        }
                    })
                SRow(Ic.refresh, Color(0xFFF1F5F9), Slate, "ক্লাউড থেকে আনুন", null) { if (viewer) vm.say("ভিউয়ার মোডে বন্ধ") else vm.fullPull() }
            }
            // ৬) অ্যাকাউন্ট
            Label("অ্যাকাউন্ট")
            Group(5) {
                var first = true
                if (signedIn) {
                    SRow(Ic.key, Color(0xFFF1F5F9), Slate, "পাসওয়ার্ড বদলান", null, divider = false, dim = viewer) {
                        val em = vm.repo.email()
                        if (viewer) vm.say("ভিউয়ার মোডে বন্ধ") else if (em != null) vm.resetPassword(em) {}
                    }
                    SRow(Ic.out, Color(0xFFFFF1F2), Rose, "লগআউট", null, chevron = false, labelColor = Rose, dim = viewer) { if (viewer) vm.say("ভিউয়ার মোডে লগআউট বন্ধ") else logoutAsk = true }
                    first = false
                }
                SRow(Ic.trash, Color(0xFFFFF1F2), Rose, "সব হিসাবের তথ্য মুছুন", null, chevron = false, labelColor = Rose, divider = !first, dim = viewer) { if (viewer) vm.say("ভিউয়ার মোডে মোছা বন্ধ") else deleteAsk = true }
            }
            Text("স্মার্ট হিসাব · সংস্করণ ০.১.০", Modifier.fillMaxWidth().padding(vertical = 18.dp), fontSize = 12.sp, lineHeight = 16.sp, color = Color(0xFF94A3B8), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }

    if (showWx) WeatherSheet(
        weather, wLoading, vm.wx.hasKey,
        onRefresh = { vm.refreshWeather(true) },
        onLive = { if (!hasLoc()) liveLauncher.launch(locPerms) else vm.refreshWeather(true, true) },
        onDismiss = { showWx = false },
    )
    folder?.let { fo ->
        when (edit) {
            "goal" -> EditValueDialog("মাসিক আয়ের লক্ষ্য", "লক্ষ্য", fo.incomeGoal?.roundToInt()?.toString() ?: "", "৳", { edit = null }) { vm.updateFolder(fo.copy(incomeGoal = Fmt.parseMoney(it).takeIf { v -> v > 0 }), "সংরক্ষিত হয়েছে") }
            "limit" -> EditValueDialog("মাসিক খরচের সীমা", "সীমা (৮০% পেরোলে সতর্কতা)", fo.expenseLimit?.roundToInt()?.toString() ?: "", "৳", { edit = null }) { vm.updateFolder(fo.copy(expenseLimit = Fmt.parseMoney(it).takeIf { v -> v > 0 }), "সংরক্ষিত হয়েছে") }
            "wage" -> EditValueDialog("মজুরি রেট", "প্রতি ১০০ পিস", fo.wageRate.roundToInt().toString(), "৳", { edit = null }) { vm.updateFolder(fo.copy(wageRate = Fmt.parseMoney(it)), "সংরক্ষিত হয়েছে") }
            "target" -> EditValueDialog("দৈনিক টার্গেট", "পিস", fo.dailyTarget?.toString() ?: "", "পিস", { edit = null }) { vm.updateFolder(fo.copy(dailyTarget = Fmt.parseInt(it).takeIf { v -> v > 0 }), "সংরক্ষিত হয়েছে") }
        }
    }
    if (pinSet) PinDialog("ভিউয়ার মোড চালু করবেন?", "বন্ধ করার জন্য ৪ সংখ্যার PIN ঠিক করুন।\n\nযা পারবেন: সব তথ্য ও রিপোর্ট দেখা, সিঙ্ক।\nযা পারবেন না: নতুন এন্ট্রি, সম্পাদনা, মোছা, লগআউট, সেটিংস বদল।", "চালু করুন",
        onDismiss = { pinSet = false }) { vm.enableViewer(it) { pinSet = false } }
    if (pinExit) {
        var usePw by remember { mutableStateOf(false) }
        if (!usePw) PinDialog("ভিউয়ার মোড বন্ধ করুন", "চালু করার সময় ঠিক করা PIN দিন।", "বন্ধ করুন", onDismiss = { pinExit = false },
            extra = if (signedIn) ({ TextButton({ usePw = true }) { Text("PIN ভুলে গেছেন? পাসওয়ার্ড দিয়ে বন্ধ করুন", fontSize = 12.sp) } }) else null) { vm.disableViewer(it) { pinExit = false } }
        else {
            var pw by remember { mutableStateOf("") }
            AlertDialog({ pinExit = false }, title = { Text("অ্যাকাউন্টের পাসওয়ার্ড দিন") }, text = { Field("পাসওয়ার্ড", pw, { pw = it }, password = true) },
                confirmButton = { TextButton({ vm.disableViewerPw(pw) { pinExit = false } }) { Text("বন্ধ করুন") } }, dismissButton = { TextButton({ pinExit = false }) { Text("বাতিল") } })
        }
    }
    if (logoutAsk) AlertDialog({ logoutAsk = false }, title = { Text("লগআউট করবেন?", fontWeight = FontWeight.Bold) },
        text = { Text("আগে সব তথ্য ক্লাউডে সিঙ্ক হবে। আবার লগইন করলে সব ফিরে পাবেন।", color = Slate) },
        confirmButton = { TextButton({ logoutAsk = false; vm.logout() }) { Text("লগআউট", color = Rose, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton({ logoutAsk = false }) { Text("বাতিল") } })
    if (deleteAsk) AlertDialog({ deleteAsk = false }, title = { Text("সব হিসাব মুছবেন?", fontWeight = FontWeight.Bold) },
        text = { Text("সব ফোল্ডার ও এন্ট্রি মুছে যাবে, ক্লাউড থেকেও। এটা ফেরানো যাবে না।", color = Slate) },
        confirmButton = { TextButton({ deleteAsk = false; vm.deleteAll() }) { Text("চিরতরে মুছুন", color = Rose, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton({ deleteAsk = false }) { Text("বাতিল") } })
}

@Composable
private fun Label(t: String) {
    Text(t, Modifier.padding(start = 4.dp, top = 16.dp, bottom = 6.dp), fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = .4.sp, color = Gray)
}

@Composable
private fun Group(delay: Int, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier.fillMaxWidth().riseIn(delay).shadow(6.dp, shape, ambientColor = Sky500, spotColor = Sky500).background(Color.White, shape).border(1.dp, Color(0xFFE6EEF5), shape).clip(shape),
        content = content,
    )
}

/** সেটিংসের এক সারি: ছোট রঙিন আইকন, নাম, ছোট বিবরণ, ডানে মান বা সুইচ */
@Composable
private fun SRow(
    icon: String, bg: Color, fg: Color, label: String, sub: String? = null, value: String? = null,
    chevron: Boolean = true, divider: Boolean = true, labelColor: Color = RowInk, valueBold: Boolean = false, dim: Boolean = false,
    trailing: (@Composable () -> Unit)? = null, onClick: (() -> Unit)? = null,
) {
    if (divider) HorizontalDivider(Modifier.padding(start = 58.dp), color = Line)
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.bounceClick(true, onClick) else Modifier).heightIn(min = 52.dp).alpha(if (dim) .45f else 1f).padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(bg), contentAlignment = Alignment.Center) { Ico(icon, size = 18.dp, tint = fg) }
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 15.sp, lineHeight = 20.sp, color = labelColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, fontSize = 12.sp, lineHeight = 16.sp, color = Gray, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        trailing?.invoke()
        if (value != null) Text(value, fontSize = if (valueBold) 16.sp else 14.sp, lineHeight = 20.sp, fontWeight = if (valueBold) FontWeight.Bold else FontWeight.Normal, color = if (valueBold) RowInk else Gray, maxLines = 1, softWrap = false)
        if (chevron && onClick != null) Ico(Ic.chevR, size = 16.dp, tint = Color(0xFF94A3B8))
    }
}

@Composable
private fun EditValueDialog(title: String, label: String, initial: String, suffix: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var v by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Field(label, v, { v = it }, numeric = true, suffix = suffix) },
        confirmButton = { TextButton({ onSave(v); onDismiss() }) { Text("সংরক্ষণ", fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onDismiss) { Text("বাতিল") } },
    )
}

@Composable
private fun PinDialog(title: String, msg: String, ok: String, onDismiss: () -> Unit, extra: (@Composable () -> Unit)? = null, onOk: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(msg, color = Slate, fontSize = 13.sp)
                Field("৪ সংখ্যার PIN", pin, { pin = it.filter { c -> c.isDigit() || c in '০'..'৯' }.take(4) }, numeric = true, password = true)
                extra?.invoke()
            }
        },
        confirmButton = { TextButton({ onOk(pin) }, enabled = pin.length == 4) { Text(ok, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onDismiss) { Text("বাতিল") } },
    )
}
