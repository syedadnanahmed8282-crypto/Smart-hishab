package com.smarthisab.app

import com.smarthisab.app.data.EntryE
import com.smarthisab.app.data.FolderE
import com.smarthisab.app.domain.Cycle
import com.smarthisab.app.domain.totalsOf
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
        deletedAt: Long? = null,
    ) = EntryE(
        id = id,
        folderId = "f1",
        day = day.toEpochDay(),
        quantity = quantity,
        expense = expense,
        rate = rate,
        deletedAt = deletedAt,
    )

    @Test
    fun `case 1 - multiple entries on one date count as one reporting day`() {
        val date = LocalDate.of(2026, 1, 1)
        val entries = listOf(
            entry("e1", date, quantity = 50, expense = 20.0),
            entry("e2", date, quantity = 30, expense = 10.0),
            entry("e3", date, quantity = 20, expense = 0.0),
        )

        val totals = Cycle.totals(folder, entries, date)
        assertEquals(1, totals.days)
        assertEquals(1, totals.work)
        assertEquals(0, totals.off)
        assertEquals(100L, totals.pieces)
        assertEquals(30.0, totals.expense, 0.001)
        assertEquals(50.0, totals.income, 0.001) // 100 * 50 / 100 = 50.0
    }

    @Test
    fun `case 2 - seven unique entry dates complete one week`() {
        // 7 separate entry dates with gaps
        val dates = listOf(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 1, 3),
            LocalDate.of(2026, 1, 5),
            LocalDate.of(2026, 1, 8),
            LocalDate.of(2026, 1, 10),
            LocalDate.of(2026, 1, 12),
            LocalDate.of(2026, 1, 15),
        )
        val entries = dates.mapIndexed { idx, d -> entry("e$idx", d, quantity = 100, expense = 10.0) }

        val weeks = Cycle.blocks(folder, entries, dates.last(), len = 7)
        assertEquals(1, weeks.size)
        assertEquals(1, weeks[0].no)
        assertEquals(7, weeks[0].t.days)
        assertFalse("7 unique dates should complete the week (not partial)", weeks[0].partial)
        assertEquals(dates.first(), weeks[0].from)
        assertEquals(dates.last(), weeks[0].to)

        // Adding an 8th date should start Week 2
        val date8 = LocalDate.of(2026, 1, 20)
        val entriesWith8 = entries + entry("e7", date8, quantity = 80, expense = 5.0)
        val weeksWith8 = Cycle.blocks(folder, entriesWith8, date8, len = 7)
        assertEquals(2, weeksWith8.size)
        assertEquals(1, weeksWith8[1].no - weeksWith8[0].no)
        assertEquals(7, weeksWith8[0].t.days)
        assertEquals(1, weeksWith8[1].t.days)
        assertTrue("Week 2 with 1 date should be partial", weeksWith8[1].partial)
    }

    @Test
    fun `case 3 - thirty unique entry dates complete one month`() {
        val base = LocalDate.of(2026, 1, 1)
        // 30 unique entry dates (skipping alternate calendar days)
        val dates = (0 until 30).map { base.plusDays((it * 2).toLong()) }
        val entries = dates.mapIndexed { idx, d -> entry("e$idx", d, quantity = 50, expense = 10.0) }

        val months = Cycle.blocks(folder, entries, dates.last(), len = 30)
        assertEquals(1, months.size)
        assertEquals(30, months[0].t.days)
        assertFalse("30 unique dates should complete month 1", months[0].partial)
        assertEquals(dates.first(), months[0].from)
        assertEquals(dates.last(), months[0].to)

        // Adding 31st date starts Month 2
        val date31 = dates.last().plusDays(3)
        val entries31 = entries + entry("e30", date31, quantity = 60, expense = 15.0)
        val months31 = Cycle.blocks(folder, entries31, date31, len = 30)
        assertEquals(2, months31.size)
        assertEquals(30, months31[0].t.days)
        assertEquals(1, months31[1].t.days)
        assertTrue(months31[1].partial)
    }

    @Test
    fun `case 4 - missing dates do not count toward reporting periods`() {
        // Entries only on Day 1 and Day 10 (8 missing days in between)
        val d1 = LocalDate.of(2026, 1, 1)
        val d2 = LocalDate.of(2026, 1, 10)
        val entries = listOf(
            entry("e1", d1, quantity = 100, expense = 20.0),
            entry("e2", d2, quantity = 120, expense = 30.0),
        )

        val totals = Cycle.totals(folder, entries, d2)
        // Calendar span is 10 days, but reporting days must be exactly 2
        assertEquals(2, totals.days)
        assertEquals(2, totals.work)
        assertEquals(0, totals.off)
        assertEquals(220L, totals.pieces)

        val weeks = Cycle.blocks(folder, entries, d2, len = 7)
        assertEquals(1, weeks.size)
        assertEquals(2, weeks[0].t.days)
        assertTrue("2 entry dates out of 7 is a partial week", weeks[0].partial)
    }

    @Test
    fun `case 5 - off-day expense entries count as reporting days`() {
        val d1 = LocalDate.of(2026, 1, 1) // Work day
        val d2 = LocalDate.of(2026, 1, 2) // Off day with expense
        val entries = listOf(
            entry("e1", d1, quantity = 100, expense = 15.0),
            entry("e2", d2, quantity = 0, expense = 40.0), // off-day expense
        )

        val totals = Cycle.totals(folder, entries, d2)
        assertEquals(2, totals.days)
        assertEquals(1, totals.work)
        assertEquals(1, totals.off)
    }

    @Test
    fun `case 6 - off-day expenses are included in total expenses and averages`() {
        val d1 = LocalDate.of(2026, 1, 1)
        val d2 = LocalDate.of(2026, 1, 2)
        val entries = listOf(
            entry("e1", d1, quantity = 100, expense = 20.0), // income = 50.0
            entry("e2", d2, quantity = 0, expense = 60.0),   // off-day expense
        )

        val totals = Cycle.totals(folder, entries, d2)
        assertEquals(2, totals.days)
        assertEquals(80.0, totals.expense, 0.001) // 20 + 60 = 80.0
        assertEquals(40.0, totals.avgExpense, 0.001) // 80 / 2 days = 40.0
        assertEquals(25.0, totals.avgIncome, 0.001)  // 50 / 2 days = 25.0
        assertEquals(-30.0, totals.profit, 0.001)     // 50 - 80 = -30.0
    }

    @Test
    fun `case 7 - production averages exclude off-days`() {
        val d1 = LocalDate.of(2026, 1, 1) // 100 pcs
        val d2 = LocalDate.of(2026, 1, 2) // 200 pcs
        val d3 = LocalDate.of(2026, 1, 3) // off day with expense
        val entries = listOf(
            entry("e1", d1, quantity = 100, expense = 10.0),
            entry("e2", d2, quantity = 200, expense = 20.0),
            entry("e3", d3, quantity = 0, expense = 50.0),
        )

        val totals = Cycle.totals(folder, entries, d3)
        assertEquals(3, totals.days)
        assertEquals(2, totals.work)
        assertEquals(1, totals.off)
        assertEquals(300L, totals.pieces)

        // Production average should be 300 / 2 working days = 150 pcs/day (NOT 300 / 3 = 100)
        assertEquals(150.0, totals.avgPieces, 0.001)
        assertEquals(150.0, totals.avgPiecesPerWorkDay, 0.001)
    }

    @Test
    fun `case 8 - weekly, monthly, home and graph totals remain consistent`() {
        val base = LocalDate.of(2026, 1, 1)
        val dates = (0 until 14).map { base.plusDays((it * 2).toLong()) } // 14 unique entry dates = 2 full weeks
        val entries = dates.mapIndexed { idx, d ->
            val isOff = idx % 5 == 0 // some off days
            entry("e$idx", d, quantity = if (isOff) 0 else 100, expense = 20.0)
        }

        val overallTotals = Cycle.totals(folder, entries, dates.last())
        val weeks = Cycle.blocks(folder, entries, dates.last(), len = 7)
        val months = Cycle.blocks(folder, entries, dates.last(), len = 30)
        val last7Days = Cycle.lastDays(folder, entries, dates.last(), n = 7)

        // 14 entry days = exactly 2 weeks of 7 entry days each
        assertEquals(2, weeks.size)
        assertEquals(7, weeks[0].t.days)
        assertEquals(7, weeks[1].t.days)
        assertFalse(weeks[0].partial)
        assertFalse(weeks[1].partial)

        // Sum of weekly pieces, income, expense equals overall
        val sumWeeklyPieces = weeks.sumOf { it.t.pieces }
        val sumWeeklyIncome = weeks.sumOf { it.t.income }
        val sumWeeklyExpense = weeks.sumOf { it.t.expense }
        assertEquals(overallTotals.pieces, sumWeeklyPieces)
        assertEquals(overallTotals.income, sumWeeklyIncome, 0.001)
        assertEquals(overallTotals.expense, sumWeeklyExpense, 0.001)

        // Month contains all 14 days so far
        assertEquals(1, months.size)
        assertEquals(14, months[0].t.days)
        assertEquals(overallTotals.income, months[0].t.income, 0.001)
        assertEquals(overallTotals.expense, months[0].t.expense, 0.001)

        // Graph returns 7 days of data
        assertEquals(7, last7Days.size)
        assertTrue(last7Days.all { it.exists })
    }

    @Test
    fun `case 9 - existing historical records remain intact`() {
        val d1 = LocalDate.of(2025, 10, 1)
        val d2 = LocalDate.of(2025, 10, 5)
        val entries = listOf(
            entry("h1", d1, quantity = 80, expense = 15.0),
            entry("h2", d2, quantity = 110, expense = 25.0),
        )

        // Existing historical records are accurately queried and computed
        val totals = Cycle.totals(folder, entries, d2)
        assertEquals(2, totals.days)
        assertEquals(190L, totals.pieces)
        assertEquals(40.0, totals.expense, 0.001)
        assertEquals(95.0, totals.income, 0.001) // (80*50/100) + (110*50/100) = 40 + 55 = 95
        assertEquals(55.0, totals.profit, 0.001)
    }
}
