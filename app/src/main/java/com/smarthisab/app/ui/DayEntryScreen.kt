package com.smarthisab.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.smarthisab.app.data.FolderE
import com.smarthisab.app.domain.*
import java.time.LocalDate
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToLong

/** ফর্মের এক সারি। existing = আগে থেকে ডাটাবেসে আছে; removed = সংরক্ষণে মুছে যাবে */
private data class RowUi(
    val id: String, val kind: String, val category: String?, val amount: String, val note: String?,
    val existing: Boolean, val origCategory: String? = null, val origAmount: Double = 0.0, val removed: Boolean = false,
)

private fun newRow(kind: String) = RowUi(UUID.randomUUID().toString(), kind, null, "", null, false)
private fun amt(r: RowUi) = Fmt.parseMoney(r.amount)
private fun amtText(d: Double) = if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()

/** আয়-ব্যয়: একটা তারিখের সব আয় ও খরচ এক স্ক্রিনে, এক সংরক্ষণে */
@Composable
fun DayEntryScreen(vm: AppVm, nav: NavHostController, dayArg: Long?, entryId: String?) {
    val f by vm.current.collectAsState()
    val all by vm.entries.collectAsState()
    val folder = f
    if (folder == null) {
        ScreenBox { TopBar("", "দৈনিক হিসাব", { nav.popBackStack() }); Text("আগে একটা ফোল্ডার বানান", Modifier.padding(24.dp), color = Slate) }
    } else {
        val start = remember { dayArg ?: all.firstOrNull { it.id == entryId }?.day ?: LocalDate.now().toEpochDay() }
        DayForm(vm, nav, folder, start)
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun DayForm(vm: AppVm, nav: NavHostController, f: FolderE, startDay: Long) {
    val all by vm.entries.collectAsState()
    val viewer by vm.viewer.collectAsState()
    val saving by vm.saving.collectAsState()
    val mode by vm.mode.collectAsState()
    val alt = mode == Mode.ALT
    val expCustom by vm.expenseCustom.collectAsState()
    val incCustom by vm.incomeCustom.collectAsState()
    val today = remember { LocalDate.now() }
    var day by remember { mutableStateOf(LocalDate.ofEpochDay(startDay)) }
    var pendingDay by remember { mutableStateOf<LocalDate?>(null) }
    var pickDate by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var sheetFor by remember { mutableStateOf<String?>(null) }

    // এই তারিখে আগে যা আছে সেটাই ফর্মে খোলে (তারিখ বদলালে নতুন করে লোড)
    val loaded = remember(day) { all.filter { it.deletedAt == null && it.day == day.toEpochDay() }.sortedBy { it.updatedAt } }
    val rows: SnapshotStateList<RowUi> = remember(day) {
        mutableStateListOf<RowUi>().apply {
            if (loaded.isEmpty()) { if (!alt) add(newRow("income")); add(newRow("expense")) }
            else addAll(loaded.map { RowUi(it.id, it.kind, it.category, amtText(it.amount), it.note, true, it.category, it.amount) })
        }
    }
    fun sig(l: List<RowUi>) = l.filter { it.existing || amt(it) > 0 }.joinToString(";") { "${it.id}|${it.kind}|${it.category}|${amt(it)}|${it.removed}" }
    val initialSig = remember(day) { sig(rows) }
    val dirty = sig(rows) != initialSig
    var focusId by remember(day) { mutableStateOf<String?>(null) }

    val live = rows.filter { !it.removed }
    val inc = live.filter { it.kind == "income" }.sumOf { amt(it) }
    val exp = live.filter { it.kind == "expense" }.sumOf { amt(it) }
    val nInc = live.count { it.kind == "income" && amt(it) > 0 }
    val nExp = live.count { it.kind == "expense" && amt(it) > 0 }
    val invalid = live.any { amt(it) > 0 && it.category == null }
    val locked = viewer || f.closed
    val canSave = !locked && !saving && dirty && !invalid
    val aInc by animateFloatAsState(inc.toFloat(), tween(500), label = "i")
    val aExp by animateFloatAsState(exp.toFloat(), tween(500), label = "e")
    val aBal by animateFloatAsState((inc - exp).toFloat(), tween(500), label = "b")

    fun requestDay(d: LocalDate) { if (d != day) { if (dirty) pendingDay = d else day = d } }
    fun tryExit() { if (dirty && !saving) showDiscard = true else nav.popBackStack() }
    BackHandler(enabled = dirty && !saving) { showDiscard = true }
    fun update(id: String, f2: (RowUi) -> RowUi) { val i = rows.indexOfFirst { it.id == id }; if (i >= 0) rows[i] = f2(rows[i]) }
    fun remove(id: String) {
        val r = rows.firstOrNull { it.id == id } ?: return
        if (r.existing) update(id) { it.copy(removed = !it.removed) } else rows.remove(r)
    }

    // ক্যালকুলেটর থেকে পাঠানো মান: আগের ফাঁকা সারিতে বসে, না থাকলে নতুন সারি
    var prefill by remember { mutableStateOf(vm.takePrefill()) }
    LaunchedEffect(prefill) {
        val p = prefill ?: return@LaunchedEffect
        val kind = if (p.target == "inc") "income" else "expense"
        val text = if (p.value % 1.0 == 0.0) p.value.toLong().toString() else p.value.toString()
        val target = rows.firstOrNull { !it.existing && it.kind == kind && it.amount.isEmpty() }
        if (target != null) { update(target.id) { it.copy(amount = text) }; focusId = target.id }
        else { val r = newRow(kind).copy(amount = text); rows.add(r); focusId = r.id }
        prefill = null
    }

    ScreenBox {
        TopBar(
            "${Fmt.dateY(day)} · ${if (loaded.isNotEmpty()) "সম্পাদনা" else mode.label}", if (alt) "দৈনিক খরচ" else "দৈনিক হিসাব", { tryExit() },
            right = {
                if (loaded.isNotEmpty() && !locked) Box {
                    IconButton({ menuOpen = true }) { Ico(Ic.more, size = 22.dp, tint = Sky700, sw = 3.4f) }
                    DropdownMenu(menuOpen, { menuOpen = false }) {
                        DropdownMenuItem({ Text("এই তারিখের সব হিসাব মুছুন", color = Rose) }, { menuOpen = false; showDelete = true })
                    }
                }
            },
        )
        Column(Modifier.weight(1f).padding(horizontal = 20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (locked) Banner(if (viewer) "ভিউয়ার মোড চালু, বদলানো যাবে না" else "ফোল্ডার সমাপ্ত, আগে আবার চালু করুন", "শুধু দেখা", Color(0xFFFFF1F2), Color(0xFFFECDD3), Rose)
            // তারিখ
            Card3D(Modifier.fillMaxWidth(), pad = 10.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(12.dp)).background(BtnBrush), contentAlignment = Alignment.Center) { Ico(Ic.cal, size = 18.dp, tint = Color.White) }
                    Text(Fmt.date(day), Modifier.weight(1f).bounceClick(!locked) { pickDate = true }, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Navy)
                    Chip("আজ", day == today) { requestDay(today) }
                    Chip("গতকাল", day == today.minusDays(1)) { requestDay(today.minusDays(1)) }
                }
                Text(
                    if (loaded.isEmpty()) "এই তারিখে এখনও হিসাব নেই" else "এই তারিখে ${Fmt.bn(loaded.size)}টা এন্ট্রি আছে। সংরক্ষণ করলে এই তারিখের হিসাব নতুন করে বসবে।",
                    Modifier.padding(start = 44.dp, top = 6.dp), fontSize = 12.sp, lineHeight = 17.sp, color = Slate,
                )
            }
            // লাইভ সারাংশ
            BlueCard(Modifier.fillMaxWidth(), delay = 1) {
                Row(Modifier.padding(vertical = 10.dp, horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (alt) {
                        val limit = f.expenseLimit?.takeIf { it > 0 }
                        val left = limit?.let { it - (Cycle.totals(f, all.filter { e -> e.day != day.toEpochDay() }).expense + exp) }
                        SumCell("আজকের খরচ", Fmt.taka(aExp.toDouble()), Color(0xFFFFE4E6), Pink, Modifier.weight(1f))
                        Box(Modifier.width(1.dp).height(40.dp).background(Color.White.copy(alpha = .22f)))
                        SumCell("সারি", "${Fmt.bn(nExp)}টা", Color.White, Color.White, Modifier.weight(1f))
                        Box(Modifier.width(1.dp).height(40.dp).background(Color.White.copy(alpha = .22f)))
                        SumCell("সীমার বাকি", if (left == null) "—" else Fmt.taka(left.coerceAtLeast(0.0)), Color(0xFFA7F3D0), Color(0xFF6EE7B7), Modifier.weight(1f))
                    } else {
                        SumCell("আয়", Fmt.taka(aInc.toDouble()), Color.White, Color.White, Modifier.weight(1f))
                        Box(Modifier.width(1.dp).height(40.dp).background(Color.White.copy(alpha = .22f)))
                        SumCell("খরচ", Fmt.taka(aExp.toDouble()), Color(0xFFFFE4E6), Pink, Modifier.weight(1f))
                        Box(Modifier.width(1.dp).height(40.dp).background(Color.White.copy(alpha = .22f)))
                        SumCell("বাকি", Fmt.taka(aBal.toDouble()), Color(0xFFA7F3D0), Color(0xFF6EE7B7), Modifier.weight(1f))
                    }
                }
            }
            if (!alt) DaySection("income", rows.filter { it.kind == "income" }, !locked, focusId, live.filter { it.kind == "income" }.sumOf { amt(it) },
                onAdd = { val r = newRow("income"); rows.add(r); focusId = r.id },
                onPick = { sheetFor = it }, onAmount = { id, v -> update(id) { r -> r.copy(amount = v) } },
                onRemove = { remove(it) }, onFocus = { focusId = it },
                onQuick = { v -> focusId?.let { id -> update(id) { r -> r.copy(amount = (amt(r) + v).roundToLong().toString()) } } })
            DaySection("expense", rows.filter { it.kind == "expense" }, !locked, focusId, live.filter { it.kind == "expense" }.sumOf { amt(it) },
                onAdd = { val r = newRow("expense"); rows.add(r); focusId = r.id },
                onPick = { sheetFor = it }, onAmount = { id, v -> update(id) { r -> r.copy(amount = v) } },
                onRemove = { remove(it) }, onFocus = { focusId = it },
                onQuick = { v -> focusId?.let { id -> update(id) { r -> r.copy(amount = (amt(r) + v).roundToLong().toString()) } } })
            if (invalid) Text("যে সারিতে পরিমাণ আছে তার খাত বাছুন", fontSize = 12.5.sp, lineHeight = 18.sp, color = Rose)
            Spacer(Modifier.height(8.dp))
        }
        Row(Modifier.fillMaxWidth().background(Color.White.copy(alpha = .96f)).padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton({ tryExit() }, Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(18.dp)) { Text("বাতিল", fontWeight = FontWeight.Bold, color = Sky800) }
            val n = nInc + nExp
            PrimaryButton(
                if (saving) "সংরক্ষণ হচ্ছে…" else (if (loaded.isNotEmpty()) "পরিবর্তন সংরক্ষণ করুন" else "সংরক্ষণ করুন") + if (n > 0) " (${Fmt.bn(n)}টা)" else "",
                Modifier.weight(2f), enabled = canSave,
            ) {
                val drafts = rows.filter { !it.removed && amt(it) > 0 }.map { DraftRow(it.id, it.kind, it.category, amt(it), it.note) }
                vm.saveDay(day, drafts) { nav.popBackStack() }
            }
        }
    }

    if (pickDate) CalendarSheet(day, all, { requestDay(it); pickDate = false }, { pickDate = false })
    pendingDay?.let { d ->
        AlertDialog(
            onDismissRequest = { pendingDay = null }, title = { Text("তারিখ বদলাবেন?", fontWeight = FontWeight.Bold) },
            text = { Text("এই তারিখের সংরক্ষণ না করা পরিবর্তন বাদ যাবে।", color = Slate) },
            confirmButton = { TextButton({ day = d; pendingDay = null }) { Text("বদলান", fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton({ pendingDay = null }) { Text("থাকুন") } },
        )
    }
    if (showDiscard) AlertDialog(
        onDismissRequest = { showDiscard = false }, title = { Text("পরিবর্তন বাদ দেবেন?", fontWeight = FontWeight.Bold) },
        text = { Text("সংরক্ষণ না করা পরিবর্তন হারিয়ে যাবে।", color = Slate) },
        confirmButton = { TextButton({ showDiscard = false; nav.popBackStack() }) { Text("বাদ দিন", color = Rose, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton({ showDiscard = false }) { Text("থাকুন") } },
    )
    if (showDelete) AlertDialog(
        onDismissRequest = { showDelete = false }, title = { Text("${Fmt.date(day)} এর সব হিসাব মুছবেন?", fontWeight = FontWeight.Bold) },
        text = { Text("এই তারিখের ${Fmt.bn(loaded.size)}টা এন্ট্রি মুছে যাবে। ১৫ সেকেন্ডের মধ্যে ফেরানো যাবে।", color = Slate) },
        confirmButton = { TextButton({ showDelete = false; vm.deleteDay(day) { nav.popBackStack() } }) { Text("মুছে ফেলুন", color = Rose, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton({ showDelete = false }) { Text("বাতিল") } },
    )
    sheetFor?.let { id ->
        val row = rows.firstOrNull { it.id == id }
        if (row != null) {
            val income = row.kind == "income"
            val cats = (if (income) Cats.income + incCustom else Cats.expense + expCustom).distinct()
            CategorySheet(income, row.category, cats, amt(row), { c -> update(id) { it.copy(category = c) }; sheetFor = null }, { name -> vm.addCat(row.kind, name); update(id) { it.copy(category = name) }; sheetFor = null }) { sheetFor = null }
        }
    }
}

@Composable
private fun SumCell(label: String, text: String, valueColor: Color, dot: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
            Text(" $label", fontSize = 12.sp, lineHeight = 16.sp, color = Sky100)
        }
        FitText(text, Modifier.fillMaxWidth(), maxSize = 20.sp, minSize = 11.sp, color = valueColor, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, lineHeight = 26.sp)
    }
}

@Composable
private fun DaySection(
    kind: String, rows: List<RowUi>, enabled: Boolean, focusId: String?, subtotal: Double,
    onAdd: () -> Unit, onPick: (String) -> Unit, onAmount: (String, String) -> Unit, onRemove: (String) -> Unit, onFocus: (String) -> Unit, onQuick: (Int) -> Unit,
) {
    val income = kind == "income"
    val col = if (income) Green else Rose
    val soft = if (income) Color(0xFFD1FAE5) else Color(0xFFFFE4E6)
    Card3D(Modifier.fillMaxWidth(), pad = 12.dp) {
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(soft), contentAlignment = Alignment.Center) { Ico(if (income) Ic.up else Ic.dn, size = 18.dp, tint = col, sw = 2.4f) }
            Text(if (income) "আয়" else "খরচ", Modifier.weight(1f), fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold, color = Navy)
            Text(Fmt.taka(subtotal), Modifier.clip(CircleShape).background(if (income) Color(0xFFECFDF5) else Color(0xFFFFF1F2)).padding(horizontal = 10.dp, vertical = 2.dp), fontSize = 13.sp, lineHeight = 19.sp, fontWeight = FontWeight.ExtraBold, color = col, maxLines = 1, softWrap = false)
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEach { r ->
                val changed = r.existing && !r.removed && (r.category != r.origCategory || amt(r) != r.origAmount)
                Row(Modifier.fillMaxWidth().alpha2(!r.removed), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        if (changed || r.removed) Text(if (r.removed) "সংরক্ষণে মুছে যাবে" else "বদলানো", Modifier.padding(bottom = 2.dp).clip(RoundedCornerShape(6.dp)).background(if (r.removed) Color(0xFFFFE4E6) else Color(0xFFFEF3C7)).padding(horizontal = 6.dp),
                            fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, color = if (r.removed) Rose else Amber)
                        CatPill(income, r.category, error = r.category == null && amt(r) > 0, enabled = enabled && !r.removed, strike = r.removed) { onPick(r.id) }
                    }
                    AmountBox(r.amount, income, enabled && !r.removed, r.removed, { onAmount(r.id, it.filter { c -> c.isDigit() || c == '.' }.take(10)) }) { onFocus(r.id) }
                    Box(Modifier.size(34.dp).clip(CircleShape).background(if (r.removed) Sky100 else Color(0xFFF1F5F9)).bounceClick(enabled) { onRemove(r.id) }, contentAlignment = Alignment.Center) {
                        Ico(if (r.removed) Ic.back else Ic.x, size = 16.dp, tint = if (r.removed) Sky800 else Slate, sw = 2.4f)
                    }
                }
            }
            if (enabled && focusId != null && rows.any { it.id == focusId && !it.removed }) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End)) {
                (if (income) listOf(100, 500, 1000, 2000) else listOf(20, 50, 100, 200)).forEach { v -> Chip("+ ${Fmt.bn(v)}", false) { onQuick(v) } }
            }
            Row(
                Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(14.dp)).border(1.5.dp, col.copy(alpha = .7f), RoundedCornerShape(14.dp)).bounceClick(enabled, onAdd),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Ico(Ic.plus, size = 18.dp, tint = col, sw = 2.6f)
                Text(if (income) " আয় যোগ করুন" else " খরচ যোগ করুন", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = col)
            }
        }
    }
}

private fun Modifier.alpha2(on: Boolean): Modifier = if (on) this else this.alpha(.55f)

@Composable
private fun CatPill(income: Boolean, name: String?, error: Boolean, enabled: Boolean, strike: Boolean, onClick: () -> Unit) {
    val col = if (income) Green else Rose
    val bg = if (income) Color(0xFFD1FAE5) else Color(0xFFFFE4E6)
    Row(
        Modifier.clip(RoundedCornerShape(14.dp)).background(if (name == null) Color.Transparent else bg)
            .border(1.5.dp, if (error) Rose else if (name == null) col.copy(alpha = .6f) else Color.Transparent, RoundedCornerShape(14.dp))
            .bounceClick(enabled, onClick).padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ico(if (name == null) Ic.tag else categoryIcon(name), size = 17.dp, tint = col)
        Text(" ${name ?: "খাত বাছুন"} ", fontSize = 14.sp, lineHeight = 20.sp, fontWeight = if (name == null) FontWeight.SemiBold else FontWeight.Bold, color = col, maxLines = 1, overflow = TextOverflow.Ellipsis,
            textDecoration = if (strike) TextDecoration.LineThrough else null, modifier = Modifier.weight(1f, fill = false))
        Ico(Ic.chev, size = 14.dp, tint = col, sw = 2.4f)
    }
}

@Composable
private fun AmountBox(value: String, income: Boolean, enabled: Boolean, strike: Boolean, onChange: (String) -> Unit, onFocus: () -> Unit) {
    val col = if (income) Green else Rose
    BasicTextField(
        value, onChange,
        Modifier.width(96.dp).height(40.dp).clip(RoundedCornerShape(14.dp)).background(Sky50).border(1.dp, Sky200, RoundedCornerShape(14.dp)).onFocusChanged { if (it.isFocused) onFocus() },
        enabled = enabled, singleLine = true, cursorBrush = SolidColor(col),
        textStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = col, textAlign = TextAlign.End, textDecoration = if (strike) TextDecoration.LineThrough else null),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        decorationBox = { inner ->
            Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("৳", fontSize = 13.sp, color = Slate, fontWeight = FontWeight.SemiBold)
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    if (value.isEmpty()) Text("০", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF94A3B8))
                    inner()
                }
            }
        },
    )
}

/** সারির খাত বাছাই: ডিফল্ট ও নিজের খাত, আর নতুন খাত যোগ */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CategorySheet(income: Boolean, selected: String?, cats: List<String>, amount: Double, onPick: (String) -> Unit, onNew: (String) -> Unit, onDismiss: () -> Unit) {
    val col = if (income) Green else Rose
    var name by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(if (income) "আয়ের উৎস বাছুন" else "খরচের খাত বাছুন", fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, color = Navy)
            Text("এই সারির জন্য" + if (amount > 0) " · ${Fmt.taka(amount)}" else "", fontSize = 12.sp, lineHeight = 17.sp, color = Slate, modifier = Modifier.padding(bottom = 10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                cats.forEach { c ->
                    val on = c == selected
                    Row(
                        Modifier.clip(RoundedCornerShape(14.dp)).background(if (on) (if (income) Color(0xFFD1FAE5) else Color(0xFFFFE4E6)) else Sky50)
                            .border(if (on) 2.dp else 0.dp, if (on) col else Color.Transparent, RoundedCornerShape(14.dp)).bounceClick(true) { onPick(c) }.padding(start = 9.dp, end = 12.dp, top = 7.dp, bottom = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Ico(categoryIcon(c), size = 18.dp, tint = if (on) col else Slate)
                        Text(" $c", fontSize = 14.sp, lineHeight = 20.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, color = if (on) col else Color(0xFF334155))
                        if (on) Ico(Ic.check, Modifier.padding(start = 4.dp), size = 14.dp, tint = col, sw = 3f)
                    }
                }
            }
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Field("নতুন খাতের নাম", name, { name = it.take(40) }, Modifier.weight(1f))
                TextButton({ onNew(name.trim()) }, enabled = name.isNotBlank()) { Text("যোগ", fontWeight = FontWeight.Bold) }
            }
        }
    }
}
