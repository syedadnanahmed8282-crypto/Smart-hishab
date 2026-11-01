package com.smarthisab.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.smarthisab.app.data.FolderE
import com.smarthisab.app.domain.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** ওপরের ডান কোণার ফোল্ডার বাছাই বাটন */
@Composable
fun FolderPill(vm: AppVm, nav: NavHostController, f: FolderE?) {
    var open by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    val folders by vm.folders.collectAsState()
    val viewer by vm.viewer.collectAsState()
    Box {
        Row(
            Modifier.clip(CircleShape).background(Color.White.copy(alpha = .88f)).border(1.dp, Color.White, CircleShape).clickable { open = true }
                .padding(start = 10.dp, end = 10.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Ico(if (f?.closed == true) Ic.lock else Ic.folder, size = 16.dp, tint = Sky700)
            Text(f?.name ?: "ফোল্ডার নেই", Modifier.padding(horizontal = 6.dp).widthIn(max = 130.dp), color = Sky800, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Ico(Ic.chev, Modifier.rotate(if (open) 180f else 0f), size = 14.dp, tint = Sky800, sw = 2.6f)
        }
        DropdownMenu(open, { open = false }) {
            folders.forEach { fo ->
                DropdownMenuItem(
                    text = { Column { Text(fo.name, fontWeight = if (fo.id == f?.id) FontWeight.Bold else FontWeight.Normal); Text(if (fo.closed) "সমাপ্ত" else "চলমান", fontSize = 12.sp, color = if (fo.closed) Rose else Green) } },
                    onClick = { vm.select(fo.id); open = false },
                )
            }
            HorizontalDivider()
            DropdownMenuItem({ Text("+ নতুন ফোল্ডার তৈরি করুন", color = Sky700, fontWeight = FontWeight.SemiBold) }, { open = false; nav.navigate("newfolder") })
            DropdownMenuItem({ Text("ফোল্ডার পরিচালনা") }, { open = false; nav.navigate("folders") })
            if (f != null && !viewer) {
                if (f.closed) DropdownMenuItem({ Text("আবার চালু করুন", color = Sky800, fontWeight = FontWeight.SemiBold) }, { open = false; vm.reopenFolder(f.id) })
                else DropdownMenuItem({ Text("এই চক্র শেষ করুন", color = Rose, fontWeight = FontWeight.SemiBold) }, { open = false; closing = true })
            }
        }
    }
    if (closing && f != null) CloseCycleDialog(vm, f) { closing = false }
}

@Composable
fun CloseCycleDialog(vm: AppVm, f: FolderE, onDismiss: () -> Unit) {
    val es by vm.entries.collectAsState()
    val mine = es.filter { it.folderId == f.id }
    val last = mine.maxOfOrNull { it.day }?.let { LocalDate.ofEpochDay(it) } ?: LocalDate.now()
    var useLast by remember { mutableStateOf(true) }
    var pick by remember { mutableStateOf(false) }
    var custom by remember { mutableStateOf(LocalDate.now()) }
    val end = if (useLast) last else custom
    val t = Cycle.totals(f.copy(endDay = end.toEpochDay()), mine, LocalDate.now())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("এই চক্র শেষ করবেন?", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${f.name} · ${Fmt.bn(t.days)} দিন", color = Slate)
                Text("আয় ${Fmt.taka(t.income)} · খরচ ${Fmt.taka(t.expense)} · লাভ ${Fmt.taka(t.profit)}\nকাজ ${Fmt.bn(t.work)} · বন্ধ ${Fmt.bn(t.off)} দিন", fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(useLast, { useLast = true }); Text("শেষ এন্ট্রির দিন (${Fmt.dateY(last)})") }
                Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(!useLast, { useLast = false; pick = true }); Text("অন্য তারিখ: ${Fmt.dateY(custom)}") }
                Text("• ফোল্ডার \"সমাপ্ত\" হবে, নতুন এন্ট্রি বা মোছা যাবে না\n• পরে আবার চালু করা যাবে", fontSize = 12.sp, color = Slate)
            }
        },
        confirmButton = { TextButton({ vm.closeFolder(f.id, end); onDismiss() }) { Text("কাজ শেষ করুন", color = Rose, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onDismiss) { Text("বাতিল") } },
    )
    if (pick) CalendarSheet(custom, emptyList(), { custom = it; pick = false }, { pick = false })
}

@Composable
fun FoldersScreen(vm: AppVm, nav: NavHostController) {
    val folders by vm.folders.collectAsState()
    val es by vm.entries.collectAsState()
    val viewer by vm.viewer.collectAsState()
    val today = remember { LocalDate.now() }
    var menuFor by remember { mutableStateOf<String?>(null) }
    var rename by remember { mutableStateOf<FolderE?>(null) }
    var closing by remember { mutableStateOf<FolderE?>(null) }
    var deleting by remember { mutableStateOf<FolderE?>(null) }
    ScreenBox {
        TopBar("সব ফোল্ডার", "ফোল্ডার পরিচালনা", { nav.popBackStack() })
        Column(Modifier.padding(horizontal = 20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            folders.forEach { f ->
                val mine = es.filter { it.folderId == f.id }
                val t = Cycle.totals(f, mine, today)
                Card3D(Modifier.fillMaxWidth().clickable { vm.select(f.id); nav.navigate("home") { popUpTo("home") { inclusive = true } } }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(f.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Navy)
                                Text(if (f.closed) "সমাপ্ত" else "চলমান", Modifier.clip(CircleShape).background(if (f.closed) Color(0xFFFFE4E6) else Color(0xFFD1FAE5)).padding(horizontal = 8.dp, vertical = 1.dp),
                                    fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (f.closed) Rose else Green)
                            }
                            Text("${Fmt.dateY(Cycle.start(f))} থেকে · ${Fmt.bn(t.days)} দিন · লাভ ${Fmt.taka(t.profit)}", fontSize = 12.sp, color = Slate)
                        }
                        if (!viewer) Box {
                            IconButton({ menuFor = f.id }) { Ico(Ic.more, size = 22.dp, tint = Slate, sw = 3.4f) }
                            DropdownMenu(menuFor == f.id, { menuFor = null }) {
                                DropdownMenuItem({ Text("নাম বদলান") }, { menuFor = null; rename = f })
                                DropdownMenuItem({ Text("রিপোর্ট দেখুন") }, { menuFor = null; vm.select(f.id); nav.navigate("report") })
                                if (f.closed) DropdownMenuItem({ Text("আবার চালু করুন") }, { menuFor = null; vm.reopenFolder(f.id) })
                                else DropdownMenuItem({ Text("এই চক্র শেষ করুন", color = Rose) }, { menuFor = null; closing = f })
                                HorizontalDivider()
                                DropdownMenuItem({ Text("ফোল্ডার মুছুন", color = Rose, fontWeight = FontWeight.Bold) }, { menuFor = null; deleting = f })
                            }
                        }
                    }
                }
            }
            if (!viewer) SoftButton("+ নতুন ফোল্ডার তৈরি করুন", Modifier.fillMaxWidth(), color = Sky700) { nav.navigate("newfolder") }
            Spacer(Modifier.height(24.dp))
        }
    }
    rename?.let { f ->
        var name by remember { mutableStateOf(f.name) }
        AlertDialog({ rename = null }, title = { Text("নাম বদলান") }, text = { Field("ফোল্ডারের নাম", name, { name = it }) },
            confirmButton = { TextButton({ if (name.isNotBlank()) vm.updateFolder(f.copy(name = name.trim()), "নাম বদলেছে"); rename = null }) { Text("সংরক্ষণ") } },
            dismissButton = { TextButton({ rename = null }) { Text("বাতিল") } })
    }
    closing?.let { CloseCycleDialog(vm, it) { closing = null } }
    deleting?.let { f ->
        var typed by remember { mutableStateOf("") }
        val mine = es.count { it.folderId == f.id }
        AlertDialog({ deleting = null }, title = { Text("ফোল্ডার মুছবেন?", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${f.name} ফোল্ডারের ${Fmt.bn(mine)} দিনের এন্ট্রি ও রিপোর্ট মুছে যাবে। ক্লাউড থেকেও মুছবে।", color = Slate)
                    Text("রেখে দিয়ে শুধু বন্ধ করতে চাইলে মোছার বদলে \"চক্র শেষ করুন\" ব্যবহার করুন।", fontSize = 12.sp, color = Slate)
                    Field("নিশ্চিত করতে ফোল্ডারের নাম লিখুন", typed, { typed = it })
                }
            },
            confirmButton = { TextButton({ vm.deleteFolder(f.id) { deleting = null } }, enabled = typed.trim() == f.name) { Text("চিরতরে মুছুন", color = if (typed.trim() == f.name) Rose else Color.Gray, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton({ deleting = null }) { Text("বাতিল") } })
    }
}

@Composable
fun NewFolderScreen(vm: AppVm, nav: NavHostController) {
    var name by rememberSaveable { mutableStateOf("") }
    var start by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    var goal by rememberSaveable { mutableStateOf("12000") }
    var limit by rememberSaveable { mutableStateOf("3000") }
    var wage by rememberSaveable { mutableStateOf("50") }
    var target by rememberSaveable { mutableStateOf("800") }
    var pickStart by remember { mutableStateOf(false) }
    val s = LocalDate.ofEpochDay(start)
    ScreenBox {
        TopBar("কাজের চক্র", "নতুন ফোল্ডার", { nav.popBackStack() })
        Column(Modifier.padding(horizontal = 20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Card3D(Modifier.fillMaxWidth()) {
                Field("ফোল্ডারের নাম (যেমন অক্টোবর–ডিসেম্বর)", name, { name = it })
                Spacer(Modifier.height(10.dp))
                Text("শুরুর তারিখ", fontSize = 13.sp, color = Slate)
                SoftButton(Fmt.dateY(s), Modifier.fillMaxWidth(), color = Navy, icon = Ic.cal) { pickStart = true }
                Text("শেষের তারিখ আগে ঠিক করতে হবে না। কাজ শেষ হলে \"চক্র শেষ করুন\" চাপবেন।", fontSize = 12.sp, color = Slate, modifier = Modifier.padding(top = 6.dp))
            }
            Card3D(Modifier.fillMaxWidth(), pad = 12.dp) {
                Field("কাজের মাসে আয়ের লক্ষ্য", goal, { goal = it }, numeric = true, suffix = "৳")
                Spacer(Modifier.height(8.dp))
                Field("কাজের মাসে খরচের সীমা", limit, { limit = it }, numeric = true, suffix = "৳")
                Spacer(Modifier.height(8.dp))
                Field("মজুরি রেট (প্রতি ১০০ পিস)", wage, { wage = it }, numeric = true, suffix = "৳")
                Spacer(Modifier.height(8.dp))
                Field("দৈনিক টার্গেট", target, { target = it }, numeric = true, suffix = "পিস")
            }
            Card3D(Modifier.fillMaxWidth(), tint = Sky50) {
                Text("শুরুর তারিখ থেকে গোনা হবে", fontWeight = FontWeight.Bold, color = Navy, fontSize = 13.sp)
                Text("সপ্তাহ ১: ${Fmt.range(s, s.plusDays(6))}\nকাজের মাস ১: ${Fmt.range(s, s.plusDays(29))} (৩০ দিন)", fontSize = 13.sp, color = Slate, lineHeight = 22.sp)
            }
            PrimaryButton("ফোল্ডার তৈরি করুন", Modifier.fillMaxWidth(), enabled = name.isNotBlank()) {
                vm.createFolder(name, s, null, Fmt.parseMoney(goal).takeIf { it > 0 }, Fmt.parseMoney(limit).takeIf { it > 0 }, Fmt.parseMoney(wage), Fmt.parseInt(target).takeIf { it > 0 }) {
                    nav.navigate("home") { popUpTo("home") { inclusive = true } }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (pickStart) CalendarSheet(s, emptyList(), { start = it.toEpochDay(); pickStart = false }, { pickStart = false })
}
