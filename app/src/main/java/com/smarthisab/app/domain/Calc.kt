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
    private val monthsShort = listOf("জানু", "ফেব্রু", "মার্চ", "এপ্রিল", "মে", "জুন", "জুলাই", "আগস্ট", "সেপ্টে", "অক্টো", "নভে", "ডিসে")
    fun monthShort(d: LocalDate): String = monthsShort[d.monthValue - 1]
    fun date(d: LocalDate): String = "${bn(d.dayOfMonth)} ${months[d.monthValue - 1]}"
    fun dateY(d: LocalDate): String = date(d) + " " + bn(d.year.toString())
    fun weekday(d: LocalDate): String = weekdays[d.dayOfWeek.value - 1]
    fun weekdayFull(d: LocalDate): String = weekdaysFull[d.dayOfWeek.value - 1]
    fun range(a: LocalDate, b: LocalDate): String = if (a.month == b.month) "${bn(a.dayOfMonth)}–${date(b)}" else "${date(a)} – ${date(b)}"
}

/** তিন ধরনের হিসাব। main = মালের হিসাব, alt = দৈনিক খরচ, prem = আয়-ব্যয় */
enum class Mode(val key: String, val label: String, val short: String) {
    MAIN("main", "মালের হিসাব", "মাল"),
    ALT("alt", "দৈনিক খরচ", "খরচ"),
    PREMIUM("prem", "আয়-ব্যয়", "আয়-ব্যয়");

    companion object { fun from(key: String?): Mode = entries.firstOrNull { it.key == key } ?: MAIN }
}

/** ডিফল্ট খাত। প্রতিটা ধরনে নিজের কাস্টম খাত আলাদা থাকে (Repo-তে) */
object Cats {
    val expense = listOf("নাস্তা", "ভাত", "গাড়ি ভাড়া", "অন্যান্য")
    val income = listOf("বিক্রি", "মজুরি", "বেতন", "অন্যান্য আয়")
    const val OTHER = "অন্যান্য"
}

/** আয়: মালের হিসাবে মাল × রেট, আয়-ব্যয়তে আয়-ধরনের এন্ট্রির পরিমাণ, দৈনিক খরচে সবসময় ০ */
fun incomeOf(e: EntryE): Double = when {
    e.mode == "main" -> e.quantity * e.rate / 100.0
    e.kind == "income" -> e.amount
    else -> 0.0
}

/** খরচ: মালের হিসাবে দিনের খরচ ঘর, অন্য দুই ধরনে খরচ-ধরনের এন্ট্রির পরিমাণ */
fun expenseOf(e: EntryE): Double = when {
    e.mode == "main" -> e.expense
    e.kind == "expense" -> e.amount
    else -> 0.0
}

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
    val avgPieces get() = pieces.toDouble() / max(1, days)
    val avgPiecesPerWorkDay get() = pieces.toDouble() / max(1, days)
}

fun totalsOf(es: List<EntryE>, days: Int? = null): Totals {
    val active = es.filter { it.deletedAt == null }
    val byDate = active.groupBy { it.day }
    // মালের হিসাবে মাল ০ মানে বন্ধের দিন (খরচ থাকলে সেটাও গোনা হয়)। অন্য দুই ধরনে এন্ট্রি-থাকা প্রতিটা তারিখই কাজের দিন।
    val workDays = byDate.count { (_, list) -> list.any { it.mode != "main" } || list.sumOf { it.quantity } > 0 }
    val offDays = byDate.count { (_, list) -> list.all { it.mode == "main" } && list.sumOf { it.quantity } == 0 }
    val actualDays = days ?: byDate.size
    val totalPieces = active.sumOf { it.quantity.toLong() }
    val totalIncome = active.sumOf { incomeOf(it) }
    val totalExpense = active.sumOf { expenseOf(it) }
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

    /** চক্রের শুরু = প্রথম এন্ট্রির তারিখ (এন্ট্রি না থাকলে ফোল্ডারের শুরুর তারিখ) */
    fun start(f: FolderE, es: List<EntryE>): LocalDate = uniqueEntryDates(f, es).firstOrNull() ?: start(f)

    /** চক্রের শেষ = শেষ এন্ট্রির তারিখ (এন্ট্রি না থাকলে শুরুর তারিখ) */
    fun end(f: FolderE, es: List<EntryE>, today: LocalDate = LocalDate.now()): LocalDate = uniqueEntryDates(f, es).lastOrNull() ?: start(f)

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
                    expense = dayEntries.sumOf { expenseOf(it) },
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
                    expense = dayEntries.sumOf { expenseOf(it) },
                    pieces = dayEntries.sumOf { it.quantity },
                    exists = true,
                )
            }
            padPoints + realPoints
        }
    }

    fun deltaPct(now: Double, prev: Double): Double? = if (prev <= 0.0) null else (now - prev) / prev * 100.0

    /** খাত অনুযায়ী মোট (kind = "expense" বা "income"), বড় থেকে ছোট */
    fun categoryTotals(es: List<EntryE>, kind: String): List<Pair<String, Double>> =
        es.filter { it.deletedAt == null && it.mode != "main" && it.kind == kind }
            .groupBy { it.category?.takeIf { c -> c.isNotBlank() } ?: Cats.OTHER }
            .map { (c, l) -> c to l.sumOf { it.amount } }
            .sortedByDescending { it.second }

    /** দিন অনুযায়ী সারাংশ (নতুন তারিখ আগে) — দৈনিক খরচ ও আয়-ব্যয়ের তালিকার জন্য */
    fun dayRows(es: List<EntryE>): List<DayRow> =
        es.filter { it.deletedAt == null }.groupBy { it.day }.map { (d, l) ->
            DayRow(
                LocalDate.ofEpochDay(d), l.sumOf { incomeOf(it) }, l.sumOf { expenseOf(it) }, l.size,
                l.filter { it.kind == "expense" }.mapNotNull { it.category }.distinct(),
            )
        }.sortedByDescending { it.date }

    /** এন্ট্রি-থাকা তারিখ ধরে দিনপ্রতি খরচ (সর্বোচ্চ/সর্বনিম্ন দিনের জন্য) */
    fun dayExpenses(es: List<EntryE>): List<Pair<LocalDate, Double>> =
        es.filter { it.deletedAt == null }.groupBy { it.day }.map { (d, l) -> LocalDate.ofEpochDay(d) to l.sumOf { expenseOf(it) } }.sortedBy { it.first }
}

data class DayRow(val date: LocalDate, val income: Double, val expense: Double, val count: Int, val cats: List<String>) {
    val net get() = income - expense
}
