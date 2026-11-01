package com.smarthisab.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarthisab.app.data.EntryE
import com.smarthisab.app.domain.Fmt
import java.time.LocalDate
import java.time.YearMonth

/** নিচ থেকে ওঠা ক্যালেন্ডার: যেকোনো তারিখ বাছা যায়। নীল বিন্দু = কাজ হয়েছে, লাল = বন্ধ ছিল। */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarSheet(selected: LocalDate, entries: List<EntryE>, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val today = remember { LocalDate.now() }
    var month by remember { mutableStateOf(YearMonth.from(selected)) }
    var sel by remember { mutableStateOf(selected) }
    val byDay = remember(entries) { entries.associateBy { it.day } }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                NavArrow(Ic.back) { month = month.minusMonths(1) }
                AnimatedContent(month, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(150)) }, label = "month") { m ->
                    Text("${Fmt.monthName(m.monthValue)} ${Fmt.bn(m.year.toString())}", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Navy)
                }
                NavArrow(Ic.fwd) { month = month.plusMonths(1) }
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp)) {
                listOf("শনি", "রবি", "সোম", "মঙ্গল", "বুধ", "বৃহ", "শুক্র").forEach {
                    Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Slate)
                }
            }
            val offset = (month.atDay(1).dayOfWeek.value + 1) % 7
            val len = month.lengthOfMonth()
            val rows = (offset + len + 6) / 7
            for (r in 0 until rows) {
                Row(Modifier.fillMaxWidth()) {
                    for (c in 0..6) {
                        val i = r * 7 + c
                        val dn = i - offset + 1
                        if (dn < 1 || dn > len) Spacer(Modifier.weight(1f).height(46.dp))
                        else {
                            val d = month.atDay(dn)
                            DayCell(d, byDay[d.toEpochDay()], d == sel, d == today, i, Modifier.weight(1f)) { sel = d }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)) {
                LegendItem(Sky400, "কাজ হয়েছে"); LegendItem(Color(0xFFE11D48), "বন্ধ ছিল"); LegendItem(Sky800, "নির্বাচিত")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SoftButton("আজ", Modifier.weight(1f)) { sel = today; month = YearMonth.from(today) }
                PrimaryButton("তারিখ নির্বাচন করুন", Modifier.weight(2f)) { onPick(sel) }
            }
        }
    }
}

@Composable
private fun NavArrow(path: String, onClick: () -> Unit) {
    Box(Modifier.size(38.dp).bounceClick(true, onClick).clip(RoundedCornerShape(12.dp)).background(Sky100), contentAlignment = Alignment.Center) {
        Ico(path, size = 18.dp, tint = Sky800, sw = 2.6f)
    }
}

@Composable
private fun LegendItem(c: Color, t: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(c)); Text(t, Modifier.padding(start = 5.dp), fontSize = 12.sp, color = Slate)
    }
}

@Composable
private fun DayCell(d: LocalDate, e: EntryE?, selected: Boolean, isToday: Boolean, index: Int, modifier: Modifier, onClick: () -> Unit) {
    val off = e != null && e.quantity == 0
    val shape = RoundedCornerShape(14.dp)
    var m = modifier.height(46.dp).popIn(index).bounceClick(true, onClick)
    m = when {
        selected -> m.shadow(8.dp, shape, ambientColor = Sky800, spotColor = Sky800).clip(shape).background(BtnBrush)
        off -> m.clip(shape).background(Color(0xFFFFF1F2))
        else -> m.clip(shape)
    }
    if (isToday && !selected) m = m.border(1.5.dp, Sky500, shape)
    Column(m, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(Fmt.bn(d.dayOfMonth), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (selected) Color.White else if (off) Rose else Navy)
        Box(Modifier.padding(top = 2.dp).size(5.dp).clip(CircleShape).background(
            when { selected -> Color.White; off -> Color(0xFFE11D48); e != null -> Sky400; else -> Color.Transparent }))
    }
}
