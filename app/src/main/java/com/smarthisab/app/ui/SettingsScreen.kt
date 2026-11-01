package com.smarthisab.app.ui

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.smarthisab.app.data.AuthState
import com.smarthisab.app.domain.Fmt
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(vm: AppVm, nav: NavHostController) {
    val f by vm.current.collectAsState()
    val viewer by vm.viewer.collectAsState()
    val auth by vm.auth.collectAsState()
    val syncing by vm.syncing.collectAsState()
    val last by vm.lastSync.collectAsState()
    var pinSet by remember { mutableStateOf(false) }
    var pinExit by remember { mutableStateOf(false) }
    var logoutAsk by remember { mutableStateOf(false) }
    var deleteAsk by remember { mutableStateOf(false) }
    val signedIn = auth == AuthState.In
    val folder = f

    ScreenBox {
        TopBar("অ্যাপ ও ডাটা", "সেটিংস")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // অ্যাকাউন্ট
            Card3D(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(46.dp).clip(CircleShape).background(BtnBrush), contentAlignment = Alignment.Center) { Ico(Ic.user, size = 24.dp, tint = Color.White) }
                    Column(Modifier.weight(1f)) {
                        Text(if (signedIn) (vm.repo.userName() ?: "আপনার অ্যাকাউন্ট") else "লগইন করা নেই", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Navy)
                        Text(if (signedIn) (vm.repo.email() ?: "") else "ডাটা শুধু এই ফোনে আছে", fontSize = 12.5.sp, color = Slate)
                    }
                    if (signedIn) Text("✓ সিঙ্ক চালু", Modifier.clip(CircleShape).background(Color(0xFFD1FAE5)).padding(horizontal = 9.dp, vertical = 3.dp), color = Green, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (signedIn) {
                        SoftButton("পাসওয়ার্ড বদলান", Modifier.weight(1f).alpha(if (viewer) .45f else 1f), color = Sky800, bg = Sky100) {
                            val em = vm.repo.email()
                            if (viewer) vm.say("ভিউয়ার মোডে বন্ধ") else if (em != null) vm.resetPassword(em) {}
                        }
                        SoftButton("লগআউট", Modifier.weight(1f).alpha(if (viewer) .45f else 1f), color = Rose) { if (viewer) vm.say("ভিউয়ার মোডে লগআউট বন্ধ") else logoutAsk = true }
                    } else PrimaryButton("লগইন / অ্যাকাউন্ট খুলুন", Modifier.weight(1f)) { vm.logout() }
                }
            }
            // ভিউয়ার মোড
            Card3D(Modifier.fillMaxWidth(), tint = if (viewer) Color(0xFFEEF2FF) else Color.White, border = if (viewer) Color(0xFFC7D2FE) else Sky100) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(IndigoBrush), contentAlignment = Alignment.Center) { Ico(Ic.eye, size = 22.dp, tint = Color.White) }
                    Column(Modifier.weight(1f)) {
                        Text("ভিউয়ার মোড", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Navy)
                        Text(if (viewer) "চালু · শুধু দেখা যাচ্ছে, সিঙ্ক চলছে" else "শুধু দেখা যাবে, কিছু বদলানো যাবে না", fontSize = 12.5.sp, color = Slate)
                    }
                    Switch(viewer, { if (viewer) pinExit = true else pinSet = true }, colors = SwitchDefaults.colors(checkedTrackColor = Indigo))
                }
                Text(if (viewer) "বন্ধ করতে PIN লাগবে।" else "চালু থাকলে নতুন এন্ট্রি, সম্পাদনা, মোছা ও লগআউট বন্ধ থাকবে। সিঙ্ক ও ব্যাকআপ চলবে।", Modifier.padding(top = 8.dp), fontSize = 12.sp, color = Slate)
            }
            // লক্ষ্য ও সীমা
            if (folder != null) {
                var goal by remember(folder.id) { mutableStateOf(folder.incomeGoal?.roundToInt()?.toString() ?: "") }
                var limit by remember(folder.id) { mutableStateOf(folder.expenseLimit?.roundToInt()?.toString() ?: "") }
                var wage by remember(folder.id) { mutableStateOf(folder.wageRate.roundToInt().toString()) }
                var target by remember(folder.id) { mutableStateOf(folder.dailyTarget?.toString() ?: "") }
                Card3D(Modifier.fillMaxWidth()) {
                    Text("লক্ষ্য ও সীমা · ${folder.name}", fontWeight = FontWeight.Bold, color = Navy, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Field("মাসিক আয়ের লক্ষ্য", goal, { goal = it }, numeric = true, enabled = !viewer, suffix = "৳")
                    Spacer(Modifier.height(8.dp))
                    Field("মাসিক খরচের সীমা (৮০% পেরোলে সতর্কতা)", limit, { limit = it }, numeric = true, enabled = !viewer, suffix = "৳")
                    Spacer(Modifier.height(8.dp))
                    Field("মজুরি রেট (নতুন এন্ট্রির জন্য, প্রতি ১০০ পিস)", wage, { wage = it }, numeric = true, enabled = !viewer, suffix = "৳")
                    Spacer(Modifier.height(8.dp))
                    Field("দৈনিক টার্গেট", target, { target = it }, numeric = true, enabled = !viewer, suffix = "পিস")
                    Spacer(Modifier.height(10.dp))
                    PrimaryButton("সংরক্ষণ করুন", Modifier.fillMaxWidth(), enabled = !viewer) {
                        vm.updateFolder(folder.copy(incomeGoal = Fmt.parseMoney(goal).takeIf { it > 0 }, expenseLimit = Fmt.parseMoney(limit).takeIf { it > 0 },
                            wageRate = Fmt.parseMoney(wage), dailyTarget = Fmt.parseInt(target).takeIf { it > 0 }), "সংরক্ষিত হয়েছে")
                    }
                }
            }
            // সিঙ্ক
            BlueCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) { Ico(Ic.cloud, size = 22.dp, tint = Color.White); Text("ক্লাউড সিঙ্ক", Modifier.padding(start = 8.dp), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                        Text(if (signedIn) "● সংযুক্ত" else "○ লগইন নেই", Modifier.clip(CircleShape).background(Color.White.copy(alpha = .18f)).padding(horizontal = 10.dp, vertical = 3.dp),
                            color = if (signedIn) Color(0xFF6EE7B7) else Sky100, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    val t = last?.toLongOrNull()?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM HH:mm")) }
                    Text(if (signedIn) "শেষ সিঙ্ক: ${t?.let { Fmt.bn(it) } ?: "এখনও হয়নি"}${if (viewer) " · ভিউয়ার মোডেও চলছে" else ""}" else "লগইন করলে সব হিসাব ক্লাউডে নিরাপদ থাকবে ও অন্য ফোনে রিয়েলটাইমে আসবে।",
                        color = Sky100, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(14.dp)).background(Color.White).clickable(enabled = !syncing) { vm.sync(true) }, contentAlignment = Alignment.Center) {
                            if (syncing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Sky800)
                            else Row(verticalAlignment = Alignment.CenterVertically) { Ico(Ic.refresh, size = 18.dp, tint = Sky800, sw = 2.4f); Text(" এখনই সিঙ্ক", color = Sky800, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                        }
                        Box(Modifier.weight(1f).height(42.dp).alpha(if (viewer) .45f else 1f).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = .18f))
                            .border(1.dp, Color.White.copy(alpha = .4f), RoundedCornerShape(14.dp)).clickable { if (viewer) vm.say("ভিউয়ার মোডে বন্ধ") else vm.fullPull() }, contentAlignment = Alignment.Center) {
                            Text("ক্লাউড থেকে আনুন", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
            SoftButton("সব হিসাবের তথ্য মুছুন", Modifier.fillMaxWidth().alpha(if (viewer) .4f else 1f), color = Rose, icon = Ic.trash) { if (viewer) vm.say("ভিউয়ার মোডে মোছা বন্ধ") else deleteAsk = true }
            Text("সংস্করণ ০.১.০", Modifier.align(Alignment.CenterHorizontally), fontSize = 12.sp, color = Slate)
            Spacer(Modifier.height(16.dp))
        }
    }

    if (pinSet) PinDialog("ভিউয়ার মোড চালু করবেন?", "বন্ধ করার জন্য ৪ সংখ্যার PIN ঠিক করুন।\n\nযা পারবেন: সব তথ্য ও রিপোর্ট দেখা, সিঙ্ক ও ব্যাকআপ।\nযা পারবেন না: নতুন এন্ট্রি, সম্পাদনা, মোছা, লগআউট, সেটিংস বদল।", "চালু করুন",
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
