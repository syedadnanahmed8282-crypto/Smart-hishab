package com.smarthisab.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.smarthisab.app.data.FolderE
import com.smarthisab.app.domain.*
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun EntryScreen(vm: AppVm, nav: NavHostController, entryId: String?) {
    val f by vm.current.collectAsState()
    val folder = f
    if (folder == null) {
        ScreenBox { TopBar("", "হিসাব", { nav.popBackStack() }); Text("আগে একটা ফোল্ডার বানান", Modifier.padding(24.dp), color = Slate) }
    } else EntryForm(vm, nav, folder, entryId)
}

@Composable
private fun EntryForm(vm: AppVm, nav: NavHostController, f: FolderE, entryId: String?) {
    val all by vm.entries.collectAsState()
    val viewer by vm.viewer.collectAsState()
    val existing = remember(entryId) { all.firstOrNull { it.id == entryId } }
    val today = remember { LocalDate.now() }
    var day by rememberSaveable { mutableStateOf(existing?.day ?: today.toEpochDay()) }
    var qty by rememberSaveable { mutableStateOf(existing?.quantity?.takeIf { it > 0 }?.toString() ?: "") }
    var exp by rememberSaveable { mutableStateOf(existing?.expense?.takeIf { it > 0 }?.roundToInt()?.toString() ?: "") }
    var note by rememberSaveable { mutableStateOf(existing?.note ?: "") }
    var off by rememberSaveable { mutableStateOf(existing?.quantity == 0) }
    var pick by remember { mutableStateOf(false) }
    var confirmDel by remember { mutableStateOf(false) }

    val d = LocalDate.ofEpochDay(day)
    val rate = existing?.rate ?: f.wageRate
    val q = if (off) 0 else Fmt.parseInt(qty)
    val ex = Fmt.parseMoney(exp)
    val income = q * rate / 100.0
    val locked = viewer || f.closed
    val valid = off || q > 0
    val editing = existing != null

    ScreenBox {
        TopBar(Fmt.dateY(d), if (editing) "হিসাব সংশোধন" else "নতুন হিসাব", { nav.popBackStack() })
        Column(Modifier.weight(1f).padding(horizontal = 20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (locked) Banner(if (viewer) "ভিউয়ার মোড চালু, বদলানো যাবে না" else "ফোল্ডার সমাপ্ত, আগে আবার চালু করুন", "শুধু দেখা", Color(0xFFFFF1F2), Color(0xFFFECDD3), Rose)
            if (editing && !locked) Banner("সংরক্ষণের আগে কিছুই বদলাবে না", "সম্পাদনা চলছে", Sky100, Sky200, Sky600)

            Card3D(Modifier.fillMaxWidth(), pad = 10.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable { pick = true }.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Ico(Ic.cal, size = 22.dp, tint = Sky700); Text(Fmt.date(d), Modifier.padding(start = 8.dp), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Navy); Text(" ▾", color = Sky700)
                    }
                    Chip("আজ", d == today) { day = today.toEpochDay() }
                    Chip("গতকাল", d == today.minusDays(1)) { day = today.minusDays(1).toEpochDay() }
                }
                if (d.isBefore(Cycle.start(f, all.filter { it.folderId == f.id }))) Text("চক্র ${Fmt.dateY(d)} থেকে শুরু হবে", Modifier.padding(top = 6.dp), fontSize = 12.sp, color = Amber)
            }

            Card3D(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("মালের পরিমাণ", Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = Slate, fontSize = 13.sp)
                    if (editing && existing != null && existing.quantity > 0) Text("আগে ${Fmt.bn(existing.quantity)}", Modifier.clip(androidx.compose.foundation.shape.CircleShape).background(Color(0xFFFEF3C7)).padding(horizontal = 9.dp, vertical = 2.dp), color = Amber, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(
                        Modifier.padding(start = 8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color(0xFFFFF1F2)).padding(start = 10.dp, top = 2.dp, bottom = 2.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("আজ কাজ বন্ধ", color = Rose, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Switch(off, { off = it }, Modifier.padding(start = 4.dp), colors = SwitchDefaults.colors(checkedTrackColor = Rose, uncheckedTrackColor = Pink, uncheckedBorderColor = Color.Transparent, uncheckedThumbColor = Color.White))
                    }
                }
                Spacer(Modifier.height(6.dp))
                Field("মাল (পিস)", if (off) "০" else qty, { qty = it }, numeric = true, enabled = !off && !locked, suffix = "পিস")
                if (!off) Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(100, 500, 800, 1000).forEach { n -> Chip("+ ${Fmt.bn(n)}") { qty = (Fmt.parseInt(qty) + n).toString() } }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (off) "বন্ধের দিন · আয় ৳ ০" else "আয় ${Fmt.taka(income)}", color = Green, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    f.dailyTarget?.takeIf { it > 0 && !off }?.let { Text("টার্গেটের ${Fmt.bn((q * 100f / it).roundToInt())}%", color = Slate, fontSize = 13.sp) }
                }
            }

            Card3D(Modifier.fillMaxWidth()) {
                Field("দৈনিক খরচ", exp, { exp = it }, numeric = true, enabled = !locked, suffix = "৳")
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(50, 100, 200, 500).forEach { n -> Chip("+ ${Fmt.bn(n)}") { exp = (Fmt.parseMoney(exp).roundToInt() + n).toString() } }
                }
            }
            Card3D(Modifier.fillMaxWidth()) { Field("মন্তব্য (ঐচ্ছিক)", note, { note = it }, enabled = !locked) }

            if (editing && existing != null) Card3D(Modifier.fillMaxWidth(), tint = Sky50) {
                Text("এই সংশোধনে যা বদলাবে", fontWeight = FontWeight.Bold, color = Navy, fontSize = 14.sp)
                DiffRow("মাল", Fmt.bn(existing.quantity), Fmt.bn(q), (q - existing.quantity).toDouble(), true)
                DiffRow("আয়", Fmt.taka(incomeOf(existing)), Fmt.taka(income), income - incomeOf(existing), true)
                DiffRow("খরচ", Fmt.taka(existing.expense), Fmt.taka(ex), ex - existing.expense, false)
                DiffRow("লাভ", Fmt.taka(incomeOf(existing) - existing.expense), Fmt.taka(income - ex), (income - ex) - (incomeOf(existing) - existing.expense), true)
                Text("সাপ্তাহিক ও মাসিক রিপোর্টও নিজে বদলে যাবে", fontSize = 12.sp, color = Slate, modifier = Modifier.padding(top = 4.dp))
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (editing && !locked) Box(
                Modifier.size(50.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFFFFF1F2)).border(1.dp, Color(0xFFFECDD3), RoundedCornerShape(18.dp)).clickable { confirmDel = true },
                contentAlignment = Alignment.Center,
            ) { Ico(Ic.trash, size = 22.dp, tint = Rose) }
            PrimaryButton(if (editing) "সংশোধন সংরক্ষণ করুন" else "সংরক্ষণ করুন", Modifier.weight(1f), enabled = !locked && valid) {
                vm.saveEntry(existing?.id, d, q, ex, note) { nav.popBackStack() }
            }
        }
    }
    if (pick) CalendarSheet(d, all.filter { it.folderId == f.id }, { day = it.toEpochDay(); pick = false }, { pick = false })
    if (confirmDel && existing != null) AlertDialog(
        onDismissRequest = { confirmDel = false },
        title = { Text("${Fmt.date(d)} এর হিসাব মুছবেন?", fontWeight = FontWeight.Bold) },
        text = { Text("• মোট আয়, খরচ ও লাভ থেকে এই দিনের অংশ বাদ যাবে\n• সাপ্তাহিক ও মাসিক রিপোর্ট নতুন করে হিসাব হবে\n• ১৫ সেকেন্ডের মধ্যে ফেরানো যাবে", color = Slate, fontSize = 14.sp, lineHeight = 22.sp) },
        confirmButton = { TextButton({ confirmDel = false; vm.deleteEntry(existing) { nav.popBackStack() } }) { Text("মুছে ফেলুন", color = Rose, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton({ confirmDel = false }) { Text("বাতিল") } },
    )
}

@Composable
private fun DiffRow(label: String, old: String, new: String, diff: Double, goodUp: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(.8f), color = Slate, fontSize = 13.sp)
        Text(old, Modifier.weight(1f), color = Color(0xFF64748B), fontSize = 13.sp, textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough)
        Text(new, Modifier.weight(1f), color = Navy, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        val up = diff >= 0
        val good = (diff == 0.0) || (up == goodUp)
        Text(if (diff == 0.0) "—" else "${if (up) "▲" else "▼"} ${Fmt.bn(kotlin.math.abs(diff).roundToInt())}", Modifier.weight(.8f), color = if (good) Green else Rose, fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}
