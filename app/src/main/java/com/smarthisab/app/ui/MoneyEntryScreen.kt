package com.smarthisab.app.ui

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.smarthisab.app.data.FolderE
import com.smarthisab.app.domain.*
import java.time.LocalDate
import kotlin.math.roundToLong

/** দৈনিক খরচ ও আয়-ব্যয়ের এন্ট্রি: তারিখ, পরিমাণ, খাত, মন্তব্য। একদিনে একাধিক এন্ট্রি চলে। */
@Composable
fun MoneyEntryScreen(vm: AppVm, nav: NavHostController, entryId: String?) {
    val f by vm.current.collectAsState()
    val folder = f
    if (folder == null) {
        ScreenBox { TopBar("", "হিসাব", { nav.popBackStack() }); Text("আগে একটা ফোল্ডার বানান", Modifier.padding(24.dp), color = Slate) }
    } else MoneyForm(vm, nav, folder, entryId)
}

private fun catIcon(name: String): String = when (name) {
    "নাস্তা" -> Ic.cup; "ভাত" -> Ic.bowl; "গাড়ি ভাড়া" -> Ic.bus; "বিক্রি" -> Ic.cart; "মজুরি" -> Ic.cash; "বেতন" -> Ic.wallet
    Cats.OTHER, "অন্যান্য আয়" -> Ic.dots; else -> Ic.tag
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MoneyForm(vm: AppVm, nav: NavHostController, f: FolderE, entryId: String?) {
    val mode by vm.mode.collectAsState()
    val all by vm.entries.collectAsState()
    val viewer by vm.viewer.collectAsState()
    val expCustom by vm.expenseCustom.collectAsState()
    val incCustom by vm.incomeCustom.collectAsState()
    val existing = remember(entryId) { all.firstOrNull { it.id == entryId } }
    val today = remember { LocalDate.now() }
    var day by rememberSaveable { mutableStateOf(existing?.day ?: today.toEpochDay()) }
    var kind by rememberSaveable { mutableStateOf(if (mode == Mode.ALT) "expense" else existing?.kind ?: "expense") }
    var amount by rememberSaveable { mutableStateOf(existing?.amount?.takeIf { it > 0 }?.roundToLong()?.toString() ?: "") }
    var cat by rememberSaveable { mutableStateOf(existing?.category) }
    var note by rememberSaveable { mutableStateOf(existing?.note ?: "") }
    var pick by remember { mutableStateOf(false) }
    var addCat by remember { mutableStateOf(false) }
    val d = LocalDate.ofEpochDay(day)
    val income = kind == "income"
    val cats = (if (income) Cats.income + incCustom else Cats.expense + expCustom).distinct()
    val selected = cat?.takeIf { it in cats } ?: cats.first()
    val amt = Fmt.parseMoney(amount)
    val locked = viewer || f.closed
    val editing = existing != null
    val dayEntries = all.filter { it.day == day && it.id != entryId }
    val dayExp = dayEntries.sumOf { expenseOf(it) }
    val dayInc = dayEntries.sumOf { incomeOf(it) }
    val accent = if (income) Green else Rose

    ScreenBox {
        TopBar("${Fmt.dateY(d)} · ${mode.label}", if (editing) "এন্ট্রি সংশোধন" else if (mode == Mode.ALT) "নতুন খরচ" else "নতুন এন্ট্রি", { nav.popBackStack() })
        Column(Modifier.weight(1f).padding(horizontal = 20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (locked) Banner(if (viewer) "ভিউয়ার মোড চালু, বদলানো যাবে না" else "ফোল্ডার সমাপ্ত, আগে আবার চালু করুন", "শুধু দেখা", Color(0xFFFFF1F2), Color(0xFFFECDD3), Rose)
            // তারিখ
            Card3D(Modifier.fillMaxWidth(), pad = 10.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(12.dp)).background(BtnBrush), contentAlignment = Alignment.Center) { Ico(Ic.cal, size = 18.dp, tint = Color.White) }
                    Text(Fmt.date(d), Modifier.weight(1f).bounceClick(!locked) { pick = true }, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Navy)
                    Chip("আজ", d == today) { day = today.toEpochDay() }
                    Chip("গতকাল", d == today.minusDays(1)) { day = today.minusDays(1).toEpochDay() }
                }
            }
            // আয় | খরচ (শুধু আয়-ব্যয়)
            if (mode == Mode.PREMIUM) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).border(1.dp, Sky100, RoundedCornerShape(18.dp)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("income" to "আয়", "expense" to "খরচ").forEach { (k, t) ->
                    val on = kind == k
                    Row(
                        Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(14.dp)).background(if (on) (if (k == "income") Green else Rose) else Color.Transparent).bounceClick(!locked) { kind = k; cat = null },
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Ico(if (k == "income") Ic.up else Ic.dn, size = 18.dp, tint = if (on) Color.White else Slate, sw = 2.4f)
                        Text(" $t", fontSize = 15.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, color = if (on) Color.White else Slate)
                    }
                }
            }
            // পরিমাণ
            Card3D(Modifier.fillMaxWidth(), pad = 12.dp) {
                Field(if (income) "আয়ের পরিমাণ" else "খরচের পরিমাণ", amount, { amount = it }, numeric = true, enabled = !locked, suffix = "৳")
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (if (income) listOf(100, 500, 1000, 2000) else listOf(20, 50, 100, 200)).forEach { v ->
                        Chip("+ ${Fmt.bn(v)}", false) { if (!locked) amount = (Fmt.parseMoney(amount) + v).roundToLong().toString() }
                    }
                }
                Text(
                    if (dayEntries.isEmpty()) "এই তারিখে এখনও এন্ট্রি নেই" else "এই তারিখে আগের এন্ট্রি: খরচ ${Fmt.taka(dayExp)}" + if (mode == Mode.PREMIUM) " · আয় ${Fmt.taka(dayInc)}" else "",
                    Modifier.padding(top = 8.dp), fontSize = 12.5.sp, lineHeight = 18.sp, color = Slate,
                )
            }
            // খাত
            Card3D(Modifier.fillMaxWidth(), pad = 12.dp) {
                Text(if (income) "আয়ের উৎস" else "খাত", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Slate)
                FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    cats.forEach { c ->
                        val on = c == selected
                        Row(
                            Modifier.clip(RoundedCornerShape(14.dp)).background(if (on) (if (income) Color(0xFFD1FAE5) else Color(0xFFFFE4E6)) else Sky50)
                                .border(if (on) 2.dp else 0.dp, if (on) accent else Color.Transparent, RoundedCornerShape(14.dp)).bounceClick(!locked) { cat = c }.padding(start = 9.dp, end = 12.dp, top = 7.dp, bottom = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Ico(catIcon(c), size = 18.dp, tint = if (on) accent else Slate)
                            Text(" $c", fontSize = 14.sp, lineHeight = 20.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, color = if (on) accent else Color(0xFF334155))
                        }
                    }
                    Row(
                        Modifier.clip(RoundedCornerShape(14.dp)).border(1.5.dp, Sky400, RoundedCornerShape(14.dp)).bounceClick(!locked) { addCat = true }.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) { Ico(Ic.plus, size = 16.dp, tint = Sky700, sw = 2.4f); Text(" নতুন খাত", fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, color = Sky700) }
                }
            }
            // মন্তব্য
            Card3D(Modifier.fillMaxWidth(), pad = 12.dp) { Field("মন্তব্য (ঐচ্ছিক)", note, { note = it }, enabled = !locked) }
            Spacer(Modifier.height(8.dp))
        }
        Row(Modifier.fillMaxWidth().background(Color.White.copy(alpha = .96f)).padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton({ nav.popBackStack() }, Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(18.dp)) { Text("বাতিল", fontWeight = FontWeight.Bold, color = Sky800) }
            PrimaryButton(if (income) "আয় সংরক্ষণ করুন" else "সংরক্ষণ করুন", Modifier.weight(2f), enabled = !locked && amt > 0) {
                vm.saveMoney(entryId, d, kind, amt, selected, note.ifBlank { null }) { nav.popBackStack() }
            }
        }
    }
    if (pick) CalendarSheet(d, all, { day = it.toEpochDay(); pick = false }, { pick = false })
    if (addCat) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addCat = false }, title = { Text(if (income) "নতুন আয়ের উৎস" else "নতুন খাত", fontWeight = FontWeight.Bold) },
            text = { Field("নাম", name, { name = it.take(40) }) },
            confirmButton = { TextButton({ vm.addCat(kind, name); cat = name.trim(); addCat = false }, enabled = name.isNotBlank()) { Text("যোগ করুন", fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton({ addCat = false }) { Text("বাতিল") } },
        )
    }
}
