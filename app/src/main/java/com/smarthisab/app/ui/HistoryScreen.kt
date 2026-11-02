package com.smarthisab.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.smarthisab.app.data.EntryE
import com.smarthisab.app.domain.*
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(vm: AppVm, nav: NavHostController) {
    val f by vm.current.collectAsState()
    val es by vm.currentEntries.collectAsState()
    val viewer by vm.viewer.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(0) }
    var sel by remember { mutableStateOf<EntryE?>(null) }
    var del by remember { mutableStateOf<EntryE?>(null) }
    val today = remember { LocalDate.now() }
    val folder = f

    ScreenBox {
        // শিরোনাম একটু ওপরে, ডানে ছোট সার্চ বার
        TopBar("", "দৈনিক হিসাব", right = { CompactSearch(query) { query = it } })
        if (folder == null) {
            Text("কোনো ফোল্ডার নেই", Modifier.padding(24.dp), color = Slate)
        } else {
            val locked = viewer || folder.closed
            val t = Cycle.totals(folder, es, today)
            val list = es.filter {
                (filter == 0 || (filter == 1 && it.quantity > 0) || (filter == 2 && it.quantity == 0)) &&
                    (query.isBlank() || Fmt.date(LocalDate.ofEpochDay(it.day)).contains(query.trim()) || (it.note ?: "").contains(query.trim(), true))
            }
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StatsRow(t)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { FolderPill(vm, nav, folder) }
                    Spacer(Modifier.width(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip("সব ${Fmt.bn(t.work + t.off)}", filter == 0) { filter = 0 }
                        Chip("কাজ ${Fmt.bn(t.work)}", filter == 1, Green) { filter = 1 }
                        Chip("বন্ধ ${Fmt.bn(t.off)}", filter == 2, Rose) { filter = 2 }
                    }
                }
                if (viewer) Banner("শুধু দেখা যাবে, বদলানো যাবে না", "ভিউয়ার মোড", Color(0xFFEEF2FF), Color(0xFFC7D2FE), Indigo)
                else if (folder.closed) Banner("নতুন এন্ট্রি বা মোছা যাবে না", "সমাপ্ত ফোল্ডার", Color(0xFFFFF1F2), Color(0xFFFECDD3), Rose)
            }
            LazyColumn(contentPadding = PaddingValues(20.dp, 10.dp, 20.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(list, key = { _, it -> it.id }) { i, e -> EntryRow(e, if (locked) null else ({ sel = e }), Modifier.riseIn(i)) }
                if (list.isEmpty()) item { Text("কোনো হিসাব পাওয়া যায়নি", color = Slate, modifier = Modifier.padding(8.dp)) }
            }
        }
    }

    sel?.let { e ->
        val d = LocalDate.ofEpochDay(e.day)
        ModalBottomSheet({ sel = null }, containerColor = Color.White) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                Text(Fmt.dateY(d), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Navy)
                Text(if (e.quantity == 0) "বন্ধের দিন" else "${Fmt.bn(e.quantity)} পিস · আয় ${Fmt.taka(incomeOf(e))} · খরচ ${Fmt.taka(e.expense)}", color = Slate, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                SheetAction(Ic.edit, "সম্পাদনা করুন", "মাল, খরচ বা তারিখ ঠিক করুন", Navy) { sel = null; nav.navigate("entry?id=${e.id}") }
                SheetAction(Ic.trash, "মুছে ফেলুন", "এই দিনের হিসাব মুছে যাবে", Rose) { sel = null; del = e }
            }
        }
    }
    del?.let { e ->
        AlertDialog(
            onDismissRequest = { del = null },
            title = { Text("${Fmt.date(LocalDate.ofEpochDay(e.day))} এর হিসাব মুছবেন?", fontWeight = FontWeight.Bold) },
            text = { Text("• মোট আয়, খরচ ও লাভ থেকে এই দিনের অংশ বাদ যাবে\n• সাপ্তাহিক ও মাসিক রিপোর্ট নতুন করে হিসাব হবে\n• ১৫ সেকেন্ডের মধ্যে ফেরানো যাবে", color = Slate, fontSize = 14.sp, lineHeight = 22.sp) },
            confirmButton = { TextButton({ del = null; vm.deleteEntry(e) }) { Text("মুছে ফেলুন", color = Rose, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton({ del = null }) { Text("বাতিল") } },
        )
    }
}

/** শিরোনামের ডানে বসানো ছোট সার্চ বার */
@Composable
private fun CompactSearch(value: String, onChange: (String) -> Unit) {
    Row(
        Modifier.width(158.dp).height(40.dp).shadow(6.dp, CircleShape, ambientColor = Sky500, spotColor = Sky500)
            .background(Color.White, CircleShape).border(1.dp, Sky100, CircleShape).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ico(Ic.search, size = 18.dp, tint = Slate)
        BasicTextField(
            value, onChange, Modifier.weight(1f).padding(start = 8.dp), singleLine = true,
            textStyle = TextStyle(fontSize = 14.sp, color = Navy), cursorBrush = SolidColor(Sky700),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text("খুঁজুন", fontSize = 14.sp, color = Color(0xFF64748B))
                    inner()
                }
            },
        )
        if (value.isNotEmpty()) Ico(Ic.x, Modifier.bounceClick(true) { onChange("") }, size = 16.dp, tint = Slate)
    }
}

@Composable
private fun SheetAction(icon: String, title: String, sub: String, color: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().bounceClick(true, onClick).clip(RoundedCornerShape(14.dp)).padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(if (color == Rose) Color(0xFFFFE4E6) else Sky100), contentAlignment = Alignment.Center) { Ico(icon, size = 20.dp, tint = color) }
        Column { Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = color); Text(sub, fontSize = 12.sp, color = Slate) }
    }
}
