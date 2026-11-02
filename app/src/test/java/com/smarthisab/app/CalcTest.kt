package com.smarthisab.app

import com.smarthisab.app.data.EntryE
import com.smarthisab.app.data.FolderE
import com.smarthisab.app.domain.Cycle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CalcTest {

    private val folder = FolderE(
        id = "f1",
        name = "টেস্ট চক্র",
        startDay = LocalDate.of(2026, 1, 1).toEpochDay(),
        wageRate = 50.0,
    )

    private fun entry(
        id: String,
        day: LocalDate,
        quantity: Int,
        expense: Double,
        rate: Double = 50.0,
        folderId: String = "f1",
        deletedAt: Long? = null,
    ) = EntryE(
        id = id,
        folderId = folderId,
        day = day.toEpochDay(),
        quantity = quantity,
        expense = expense,
        rate = rate,
        deletedAt = deletedAt,
    )

    @Test
    fun `case 1 - five work days plus two off days equals seven day average`() {
        // User's exact prompt example:
        // Day 1: work 50 pcs
        // Day 2: work 40 pcs
        // Day 3: off 0 pcs
        // Day 4: work 30 pcs
        // Day 5: off 0 pcs
        // Day 6: work 20 pcs
        // Day 7: work 10 pcs
        // Total pieces = 150 pcs, Total Unique Entry Dates = 7
        // Correct average = 150 / 7 = 21.43 pcs (NOT 150 / 5 = 30 pcs)
        val base = LocalDate.of(2026, 1, 1)
        val entries = listOf(
            entry("e1", base.plusDays(0), quantity = 50, expense = 10.0),
            entry("e2", base.plusDays(1), quantity = 40, expense = 10.0),
            entry("e3", base.plusDays(2), quantity = 0, expense = 20.0),  // off day
            entry("e4", base.plusDays(3), quantity = 30, expense = 10.0),
            entry("e5", base.plusDays(4), quantity = 0, expense = 15.0),  // off day
            entry("e6", base.plusDays(5), quantity = 20, expense = 10.0),
            entry("e7", base.plusDays(6), quantity = 10, expense = 10.0),
        )

        val totals = Cycle.totals(folder, entries, base.plusDays(6))
        assertEquals(7, totals.days)
        assertEquals(5, totals.work)
        assertEquals(2, totals.off)
        assertEquals(150L, totals.pieces)

        val expectedAvg = 150.0 / 7.0 // 21.4285714...
        assertEquals(expectedAvg, totals.avgPieces, 0.001)
        assertEquals(expectedAvg, totals.avgPiecesPerWorkDay, 0.001)
    }

    @Test
    fun `case 2 - three entries on same date count as one day`() {
        val date = LocalDate.of(2026, 1, 1)
        val entries = listOf(
            entry("e1", date, quantity = 50, expense = 20.0),
            entry("e2", date, quantity = 30, expense = 10.0),
            entry("e3", date, quantity = 20, expense = 5.0),
        )

        val totals = Cycle.totals(folder, entries, date)
        assertEquals(1, totals.days)
        assertEquals(1, totals.work)
        assertEquals(0, totals.off)
        assertEquals(100L, totals.pieces)
        assertEquals(35.0, totals.expense, 0.001)
        assertEquals(100.0, totals.avgPieces, 0.001) // 100 / 1 = 100.0
    }

    @Test
    fun `case 3 - missing calendar days without entries do not count`() {
        val d1 = LocalDate.of(2026, 1, 1)
        val d7 = LocalDate.of(2026, 1, 7) // 5 calendar days (Jan 2..6) had NO entries
        val entries = listOf(
            entry("e1", d1, quantity = 100, expense = 20.0),
            entry("e2", d7, quantity = 100, expense = 20.0),
        )

        val totals = Cycle.totals(folder, entries, d7)
        assertEquals(2, totals.days) // only 2 unique entry dates
        assertEquals(2, totals.work)
        assertEquals(0, totals.off)
        assertEquals(200L, totals.pieces)
        assertEquals(100.0, totals.avgPieces, 0.001) // 200 / 2 = 100.0 (NOT 200 / 7)
    }

    @Test
    fun `case 4 - off-day expense is included in total expense and average expense`() {
        val d1 = LocalDate.of(2026, 1, 1)
        val d2 = LocalDate.of(2026, 1, 2)
        val entries = listOf(
            entry("e1", d1, quantity = 100, expense = 20.0),
            entry("e2", d2, quantity = 0, expense = 60.0), // off-day expense
        )

        val totals = Cycle.totals(folder, entries, d2)
        assertEquals(2, totals.days)
        assertEquals(80.0, totals.expense, 0.001) // 20 + 60
        assertEquals(40.0, totals.avgExpense, 0.001) // 80 / 2 = 40.0
        assertEquals(25.0, totals.avgIncome, 0.001)  // 50 / 2 = 25.0
        assertEquals(50.0, totals.avgPieces, 0.001)  // 100 / 2 = 50.0
    }

    @Test
    fun `case 5 - seven unique entry dates complete one weekly cycle`() {
        val dates = listOf(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 1, 3),
            LocalDate.of(2026, 1, 6),
            LocalDate.of(2026, 1, 8),
            LocalDate.of(2026, 1, 11),
            LocalDate.of(2026, 1, 14),
            LocalDate.of(2026, 1, 18),
        )
        val entries = dates.mapIndexed { idx, d -> entry("e$idx", d, quantity = 70, expense = 10.0) }

        val weeks = Cycle.blocks(folder, entries, dates.last(), len = 7)
        assertEquals(1, weeks.size)
        assertEquals(7, weeks[0].t.days)
        assertFalse("7 unique dates complete the week", weeks[0].partial)
        assertEquals(dates.first(), weeks[0].from)
        assertEquals(dates.last(), weeks[0].to)
    }

    @Test
    fun `case 6 - thirty unique entry dates complete one monthly cycle`() {
        val base = LocalDate.of(2026, 1, 1)
        val dates = (0 until 30).map { base.plusDays((it * 2).toLong()) }
        val entries = dates.mapIndexed { idx, d -> entry("e$idx", d, quantity = 50, expense = 10.0) }

        val months = Cycle.blocks(folder, entries, dates.last(), len = 30)
        assertEquals(1, months.size)
        assertEquals(30, months[0].t.days)
        assertFalse("30 unique dates complete the month", months[0].partial)
        assertEquals(dates.first(), months[0].from)
        assertEquals(dates.last(), months[0].to)
    }

    @Test
    fun `case 7 - deleted entries are excluded from all calculations`() {
        val d1 = LocalDate.of(2026, 1, 1)
        val d2 = LocalDate.of(2026, 1, 2)
        val entries = listOf(
            entry("e1", d1, quantity = 100, expense = 20.0),
            entry("e2", d2, quantity = 80, expense = 15.0, deletedAt = System.currentTimeMillis()), // DELETED
        )

        val totals = Cycle.totals(folder, entries, d2)
        assertEquals(1, totals.days)
        assertEquals(1, totals.work)
        assertEquals(100L, totals.pieces)
        assertEquals(20.0, totals.expense, 0.001)
        assertEquals(100.0, totals.avgPieces, 0.001)
    }

    @Test
    fun `case 8 - entries from other folders are excluded`() {
        val d1 = LocalDate.of(2026, 1, 1)
        val entries = listOf(
            entry("e1", d1, quantity = 100, expense = 20.0, folderId = "f1"),
            entry("e2", d1, quantity = 500, expense = 200.0, folderId = "other_folder"), // OTHER FOLDER
        )

        val totals = Cycle.totals(folder, entries, d1)
        assertEquals(1, totals.days)
        assertEquals(100L, totals.pieces)
        assertEquals(20.0, totals.expense, 0.001)
        assertEquals(100.0, totals.avgPieces, 0.001)
    }

    @Test
    fun `case 9 - empty data and zero division are safely handled`() {
        val emptyEntries = emptyList<EntryE>()
        val totals = Cycle.totals(folder, emptyEntries, LocalDate.of(2026, 1, 1))

        assertEquals(0, totals.days)
        assertEquals(0, totals.work)
        assertEquals(0, totals.off)
        assertEquals(0L, totals.pieces)
        assertEquals(0.0, totals.income, 0.001)
        assertEquals(0.0, totals.expense, 0.001)
        assertEquals(0.0, totals.avgPieces, 0.001)
        assertEquals(0.0, totals.avgIncome, 0.001)
        assertEquals(0.0, totals.avgExpense, 0.001)
        assertEquals(0.0, totals.avgPiecesPerWorkDay, 0.001)

        val blocks = Cycle.blocks(folder, emptyEntries, LocalDate.of(2026, 1, 1), len = 7)
        assertEquals(1, blocks.size)
        assertTrue(blocks[0].partial)
        assertEquals(0.0, blocks[0].t.avgPieces, 0.001)

        val lastDays = Cycle.lastDays(folder, emptyEntries, LocalDate.of(2026, 1, 1), n = 7)
        assertEquals(7, lastDays.size)
        assertFalse(lastDays[0].exists)
    }

    @Test
    fun `case 10 - home report and graph totals remain consistent`() {
        val base = LocalDate.of(2026, 1, 1)
        val dates = (0 until 14).map { base.plusDays((it * 2).toLong()) }
        val entries = dates.mapIndexed { idx, d ->
            val isOff = idx % 4 == 0 // off day every 4th entry
            entry("e$idx", d, quantity = if (isOff) 0 else 100, expense = 20.0)
        }

        val overallTotals = Cycle.totals(folder, entries, dates.last())
        val weeks = Cycle.blocks(folder, entries, dates.last(), len = 7)
        val months = Cycle.blocks(folder, entries, dates.last(), len = 30)
        val last7Days = Cycle.lastDays(folder, entries, dates.last(), n = 7)

        // Consistency across blocks and totals
        assertEquals(2, weeks.size)
        assertEquals(overallTotals.pieces, weeks.sumOf { it.t.pieces })
        assertEquals(overallTotals.income, weeks.sumOf { it.t.income }, 0.001)
        assertEquals(overallTotals.expense, weeks.sumOf { it.t.expense }, 0.001)

        // Overall avgPieces uses overall days (14 unique entry dates)
        val expectedOverallAvgPieces = overallTotals.pieces.toDouble() / 14.0
        assertEquals(expectedOverallAvgPieces, overallTotals.avgPieces, 0.001)

        // Each week's avgPieces uses that week's days (7 unique entry dates)
        assertEquals(weeks[0].t.pieces.toDouble() / 7.0, weeks[0].t.avgPieces, 0.001)
        assertEquals(weeks[1].t.pieces.toDouble() / 7.0, weeks[1].t.avgPieces, 0.001)

        // Month contains all 14 entry days
        assertEquals(1, months.size)
        assertEquals(14, months[0].t.days)
        assertEquals(overallTotals.income, months[0].t.income, 0.001)
        assertEquals(overallTotals.avgPieces, months[0].t.avgPieces, 0.001)

        // Graph returns 7 entry points
        assertEquals(7, last7Days.size)
        assertTrue(last7Days.all { it.exists })
    }
}
