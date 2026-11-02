package com.smarthisab.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarthisab.app.domain.Fmt
import com.smarthisab.app.domain.Totals

/** মোট মাল · মোট আয় · মোট খরচ · মোট বাকি (হোম ও ইতিহাসে একই) — সংখ্যা বড় হলে লেখা নিজে ছোট হয়ে মাপে বসে */
@Composable
fun StatsRow(t: Totals, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        MiniStat("মোট মাল", Fmt.bn(t.pieces) + " পিস", Navy, Modifier.weight(1f))
        MiniStat("মোট আয়", Fmt.taka(t.income), Green, Modifier.weight(1f))
        MiniStat("মোট খরচ", Fmt.taka(t.expense), Rose, Modifier.weight(1f))
        MiniStat("মোট বাকি", Fmt.taka(t.profit), Sky700, Modifier.weight(1f))
    }
}

@Composable
fun MiniStat(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card3D(modifier, pad = 8.dp) {
        Text(label, fontSize = 12.sp, color = Slate, maxLines = 1, softWrap = false)
        FitText(value, Modifier.fillMaxWidth(), maxSize = 14.sp, minSize = 8.sp, color = color, fontWeight = FontWeight.ExtraBold)
    }
}

/** হোম কার্ডের "চক্রের গড়/দিন": আয়, খরচ, মাল — তিনটা সমান ঘরে */
@Composable
fun AvgCell(label: String, value: String, dot: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Androidx_Dot(dot)
        Column(Modifier.weight(1f).padding(start = 6.dp)) {
            Text(label, fontSize = 12.sp, color = Sky100, maxLines = 1, softWrap = false)
            FitText(value, Modifier.fillMaxWidth(), maxSize = 15.sp, minSize = 8.sp, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun Androidx_Dot(c: Color) { androidx.compose.foundation.layout.Box(Modifier.size(8.dp).clip(CircleShape).background(c)) }
