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

data class Totals(
    val days: Int,
    val work: Int,
    val off: Int,
    val pieces: Long,
    val income: Double,
    val expense: Double,
) {
    val profit get() = income - expense
    val avgIncome get() = income / max(1, days)
    val avgExpense get() = expense / max(1, days)
    // Production averages must use actual working days, not all entry days:
    val avgPieces get() = pieces.toDouble() / max(1, work)
    val avgPiecesPerWorkDay get() = pieces.toDouble() / max(1, work)
}

fun totalsOf(es: List<EntryE>, days: Int? = null): Totals {
    val active = es.filter { it.deletedAt == null }
    val byDate = active.groupBy { it.day }
    val workDays = byDate.count { (_, list) -> list.sumOf { it.quantity } > 0 }
    val offDays = byDate.count { (_, list) -> list.sumOf { it.quantity } == 0 }
    val actualDays = days ?: byDate.size
    val totalPieces = active.sumOf { it.quantity.toLong() }
    val totalIncome = active.sumOf { incomeOf(it) }
    val totalExpense = active.sumOf { it.expense }
    return Totals(
        days = actualDays,
        work = workDays,
        off = offDays,
        pieces = totalPieces,
        income = totalIncome,
        expense = totalExpense,
    )
}

data class Block(
    val no: Int,
    val from: LocalDate,
    val to: LocalDate,
    val partial: Boolean,
    val t: Totals,
    val dates: List<LocalDate> = emptyList(),
)

data class DayPt(val date: LocalDate, val income: Double, val expense: Double, val pieces: Int, val exists: Boolean)

/** ফোল্ডার = কাজের চক্র। এন্ট্রি-দিন ভিত্তিক সপ্তাহ (৭ দিন) ও কাজের মাস (৩০ দিন) হিসাব। */
object Cycle {
    fun start(f: FolderE): LocalDate = LocalDate.ofEpochDay(f.startDay)
    fun end(f: FolderE, today: LocalDate = LocalDate.now()): LocalDate = f.endDay?.let { LocalDate.ofEpochDay(it) } ?: today

    fun uniqueEntryDates(f: FolderE, es: List<EntryE>): List<LocalDate> {
        val active = es.filter { it.deletedAt == null && it.folderId == f.id }
        return active.map { LocalDate.ofEpochDay(it.day) }.distinct().sorted()
    }

    fun totalDays(f: FolderE, today: LocalDate = LocalDate.now()): Int =
        max(1, ChronoUnit.DAYS.between(start(f), end(f, today)).toInt() + 1)

    fun totalDays(f: FolderE, es: List<EntryE>, today: LocalDate = LocalDate.now()): Int {
        val dates = uniqueEntryDates(f, es)
        return if (dates.isEmpty()) 0 else dates.size
    }

    fun dayNo(f: FolderE, today: LocalDate = LocalDate.now()): Int =
        max(1, ChronoUnit.DAYS.between(start(f), end(f, today)).toInt() + 1)

    fun dayNo(f: FolderE, es: List<EntryE>, today: LocalDate = LocalDate.now()): Int {
        val dates = uniqueEntryDates(f, es)
        return max(1, dates.size)
    }

    fun totals(f: FolderE, es: List<EntryE>, today: LocalDate = LocalDate.now()): Totals {
        val active = es.filter { it.deletedAt == null && it.folderId == f.id }
        val dates = uniqueEntryDates(f, es)
        return totalsOf(active, days = dates.size)
    }

    /** len = ৭ (সপ্তাহ) বা ৩০ (কাজের মাস) - consecutive groups of len unique entry dates */
    fun blocks(f: FolderE, es: List<EntryE>, today: LocalDate = LocalDate.now(), len: Int): List<Block> {
        val active = es.filter { it.deletedAt == null && it.folderId == f.id }
        val byDate = active.groupBy { it.day }
        val sortedDates = byDate.keys.sorted().map { LocalDate.ofEpochDay(it) }
        if (sortedDates.isEmpty()) {
            val s = start(f)
            return listOf(Block(no = 1, from = s, to = s, partial = true, t = Totals(0, 0, 0, 0L, 0.0, 0.0), dates = emptyList()))
        }
        val chunks = sortedDates.chunked(len)
        return chunks.mapIndexed { idx, chunkDates ->
            val from = chunkDates.first()
            val to = chunkDates.last()
            val isPartial = chunkDates.size < len
            val chunkEpochDays = chunkDates.map { it.toEpochDay() }.toSet()
            val chunkEntries = active.filter { it.day in chunkEpochDays }
            Block(
                no = idx + 1,
                from = from,
                to = to,
                partial = isPartial,
                t = totalsOf(chunkEntries, days = chunkDates.size),
                dates = chunkDates,
            )
        }
    }

    fun lastDays(f: FolderE, es: List<EntryE>, today: LocalDate = LocalDate.now(), n: Int = 7): List<DayPt> {
        val active = es.filter { it.deletedAt == null && it.folderId == f.id }
        val byDate = active.groupBy { it.day }
        val sortedDates = byDate.keys.sorted().map { LocalDate.ofEpochDay(it) }
        val k = sortedDates.size
        return if (k >= n) {
            val lastNDates = sortedDates.takeLast(n)
            lastNDates.map { d ->
                val dayEntries = byDate[d.toEpochDay()] ?: emptyList()
                DayPt(
                    date = d,
                    income = dayEntries.sumOf { incomeOf(it) },
                    expense = dayEntries.sumOf { it.expense },
                    pieces = dayEntries.sumOf { it.quantity },
                    exists = true,
                )
            }
        } else {
            val firstDate = sortedDates.firstOrNull() ?: today
            val padCount = n - k
            val padPoints = (padCount downTo 1).map {
                DayPt(firstDate.minusDays(it.toLong()), 0.0, 0.0, 0, exists = false)
            }
            val realPoints = sortedDates.map { d ->
                val dayEntries = byDate[d.toEpochDay()] ?: emptyList()
                DayPt(
                    date = d,
                    income = dayEntries.sumOf { incomeOf(it) },
                    expense = dayEntries.sumOf { it.expense },
                    pieces = dayEntries.sumOf { it.quantity },
                    exists = true,
                )
            }
            padPoints + realPoints
        }
    }

    fun deltaPct(now: Double, prev: Double): Double? = if (prev <= 0.0) null else (now - prev) / prev * 100.0
}
