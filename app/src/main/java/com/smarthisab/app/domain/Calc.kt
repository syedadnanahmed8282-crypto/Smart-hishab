package com.smarthisab.app.domain

import com.smarthisab.app.data.EntryE
import com.smarthisab.app.data.FolderE
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToLong

/** বাংলা সংখ্যা ও তারিখের লেখা */
object Fmt {
    private val digits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
    private val months = listOf("জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন", "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর")
    private val weekdays = listOf("সোম", "মঙ্গল", "বুধ", "বৃহ", "শুক্র", "শনি", "রবি")
    private val weekdaysFull = listOf("সোমবার", "মঙ্গলবার", "বুধবার", "বৃহস্পতিবার", "শুক্রবার", "শনিবার", "রবিবার")

    fun bn(s: String): String = buildString { s.forEach { c -> append(if (c in '0'..'9') digits[c - '0'] else c) } }
    fun bn(n: Int): String = bn(n.toLong())
    fun bn(n: Long): String = bn(group(n))
    fun bn(d: Double, decimals: Int = 0): String = if (decimals == 0) bn(d.roundToLong()) else bn(String.format("%.${decimals}f", d))

    private fun group(n: Long): String {
        val s = abs(n).toString()
        val out = StringBuilder()
        s.reversed().forEachIndexed { i, c -> if (i > 0 && i % 3 == 0) out.append(','); out.append(c) }
        return (if (n < 0) "-" else "") + out.reverse().toString()
    }

    fun taka(v: Double): String = "৳ " + bn(v.roundToLong())
    fun ascii(s: String): String = buildString { s.forEach { c -> val i = digits.indexOf(c); append(if (i >= 0) ('0' + i) else c) } }
    fun parseInt(s: String): Int = ascii(s).filter { it.isDigit() }.take(9).toIntOrNull() ?: 0
    fun parseMoney(s: String): Double = ascii(s).filter { it.isDigit() || it == '.' }.toDoubleOrNull() ?: 0.0
    fun monthName(m: Int): String = months[m - 1]
    fun date(d: LocalDate): String = "${bn(d.dayOfMonth)} ${months[d.monthValue - 1]}"
    fun dateY(d: LocalDate): String = date(d) + " " + bn(d.year.toString())
    fun weekday(d: LocalDate): String = weekdays[d.dayOfWeek.value - 1]
    fun weekdayFull(d: LocalDate): String = weekdaysFull[d.dayOfWeek.value - 1]
    fun range(a: LocalDate, b: LocalDate): String = if (a.month == b.month) "${bn(a.dayOfMonth)}–${date(b)}" else "${date(a)} – ${date(b)}"
}

fun incomeOf(e: EntryE): Double = e.quantity * e.rate / 100.0

data class Totals(val days: Int, val work: Int, val off: Int, val pieces: Long, val income: Double, val expense: Double) {
    val profit get() = income - expense
    val avgIncome get() = income / max(1, days)
    val avgExpense get() = expense / max(1, days)
    val avgPieces get() = pieces.toDouble() / max(1, days)
    val avgPiecesPerWorkDay get() = pieces.toDouble() / max(1, work)
}

fun totalsOf(es: List<EntryE>, days: Int) = Totals(
    days, es.count { it.quantity > 0 }, es.count { it.quantity == 0 },
    es.sumOf { it.quantity.toLong() }, es.sumOf { incomeOf(it) }, es.sumOf { it.expense }
)

data class Block(val no: Int, val from: LocalDate, val to: LocalDate, val partial: Boolean, val t: Totals)
data class DayPt(val date: LocalDate, val income: Double, val expense: Double, val pieces: Int, val exists: Boolean)

/** ফোল্ডার = কাজের চক্র। সপ্তাহ ও কাজের মাস শুরুর তারিখ থেকে গোনা হয়। */
object Cycle {
    fun start(f: FolderE): LocalDate = LocalDate.ofEpochDay(f.startDay)
    fun end(f: FolderE, today: LocalDate): LocalDate = f.endDay?.let { LocalDate.ofEpochDay(it) } ?: today
    fun totalDays(f: FolderE, today: LocalDate): Int = max(1, ChronoUnit.DAYS.between(start(f), end(f, today)).toInt() + 1)
    fun dayNo(f: FolderE, today: LocalDate): Int = max(1, ChronoUnit.DAYS.between(start(f), end(f, today)).toInt() + 1)

    fun totals(f: FolderE, es: List<EntryE>, today: LocalDate): Totals = totalsOf(es, totalDays(f, today))

    /** len = ৭ (সপ্তাহ) বা ৩০ (কাজের মাস) */
    fun blocks(f: FolderE, es: List<EntryE>, today: LocalDate, len: Int): List<Block> {
        val s = start(f)
        val e = end(f, today)
        val n = (ChronoUnit.DAYS.between(s, e).toInt()) / len + 1
        return (1..n).map { i ->
            val from = s.plusDays(((i - 1) * len).toLong())
            val fullTo = from.plusDays((len - 1).toLong())
            val to = if (fullTo.isAfter(e)) e else fullTo
            val list = es.filter { val d = LocalDate.ofEpochDay(it.day); !d.isBefore(from) && !d.isAfter(to) }
            val days = ChronoUnit.DAYS.between(from, to).toInt() + 1
            Block(i, from, to, to.isBefore(fullTo), totalsOf(list, days))
        }
    }

    fun lastDays(f: FolderE, es: List<EntryE>, today: LocalDate, n: Int = 7): List<DayPt> {
        val last = end(f, today)
        return (n - 1 downTo 0).map { k ->
            val d = last.minusDays(k.toLong())
            val e = es.firstOrNull { it.day == d.toEpochDay() }
            DayPt(d, e?.let { incomeOf(it) } ?: 0.0, e?.expense ?: 0.0, e?.quantity ?: 0, e != null)
        }
    }

    fun deltaPct(now: Double, prev: Double): Double? = if (prev <= 0.0) null else (now - prev) / prev * 100.0
}
