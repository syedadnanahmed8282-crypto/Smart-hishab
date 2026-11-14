package com.smarthisab.app

import com.smarthisab.app.data.EntryE
import com.smarthisab.app.data.FolderE
import com.smarthisab.app.domain.Cats
import com.smarthisab.app.domain.Cycle
import com.smarthisab.app.domain.Mode
import com.smarthisab.app.domain.expenseOf
import com.smarthisab.app.domain.incomeOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** তিন ধরনের হিসাব (main / alt / prem) এর গণনার নিয়ম */
class CalcModesTest {
    private val d0 = LocalDate.of(2026, 10, 1)

    private fun folder(mode: String) = FolderE(id = "f-$mode", name = mode, startDay = d0.toEpochDay(), wageRate = 50.0, mode = mode)

    private fun money(id: String, mode: String, day: LocalDate, kind: String, amount: Double, cat: String? = null) =
        EntryE(id = id, folderId = "f-$mode", day = day.toEpochDay(), quantity = 0, expense = 0.0, rate = 0.0, mode = mode, kind = kind, category = cat, amount = amount)

    private fun main(id: String, day: LocalDate, qty: Int, exp: Double) =
        EntryE(id = id, folderId = "f-main", day = day.toEpochDay(), quantity = qty, expense = exp, rate = 50.0)

    @Test
    fun `mode keys round trip and default to main`() {
        assertEquals(Mode.MAIN, Mode.from(null))
        assertEquals(Mode.MAIN, Mode.from("unknown"))
        assertEquals(Mode.ALT, Mode.from("alt"))
        assertEquals(Mode.PREMIUM, Mode.from("prem"))
    }

    @Test
    fun `main income and expense use quantity rate and expense field`() {
        val e = main("a", d0, 800, 100.0)
        assertEquals(400.0, incomeOf(e), 0.001)
        assertEquals(100.0, expenseOf(e), 0.001)
    }

    @Test
    fun `alt counts only expenses and never income`() {
        val f = folder("alt")
        val es = listOf(money("1", "alt", d0, "expense", 40.0, "নাস্তা"), money("2", "alt", d0, "expense", 80.0, "ভাত"))
        val t = Cycle.totals(f, es)
        assertEquals(0.0, t.income, 0.001)
        assertEquals(120.0, t.expense, 0.001)
        assertEquals(1, t.days)   // একই দিনে দুই এন্ট্রি = এক দিন
        assertEquals(1, t.work)
        assertEquals(0, t.off)
        assertEquals(120.0, t.avgExpense, 0.001)
    }

    @Test
    fun `premium separates income and expense and computes net`() {
        val f = folder("prem")
        val es = listOf(
            money("1", "prem", d0, "income", 500.0, "বিক্রি"),
            money("2", "prem", d0, "expense", 90.0, "ভাত"),
            money("3", "prem", d0.plusDays(1), "expense", 60.0, "নাস্তা"),
        )
        val t = Cycle.totals(f, es)
        assertEquals(500.0, t.income, 0.001)
        assertEquals(150.0, t.expense, 0.001)
        assertEquals(350.0, t.profit, 0.001)
        assertEquals(2, t.days)
        assertEquals(250.0, t.avgIncome, 0.001)   // ৫০০ ÷ ২ এন্ট্রির তারিখ
    }

    @Test
    fun `dates without entries are skipped and cycle runs first to last entry date`() {
        val f = folder("alt")
        val es = listOf(
            money("1", "alt", d0.plusDays(2), "expense", 10.0),
            money("2", "alt", d0.plusDays(10), "expense", 30.0),
            money("3", "alt", d0.plusDays(40), "expense", 20.0),
        )
        assertEquals(d0.plusDays(2), Cycle.start(f, es))
        assertEquals(d0.plusDays(40), Cycle.end(f, es))
        val t = Cycle.totals(f, es)
        assertEquals(3, t.days)                    // মাঝের ফাঁকা তারিখ গোনা হয় না
        assertEquals(20.0, t.avgExpense, 0.001)    // ৬০ ÷ ৩
    }

    @Test
    fun `seven unique entry dates make a week and thirty make a month`() {
        val f = folder("alt")
        val es = (0 until 37).map { money("e$it", "alt", d0.plusDays((it * 2).toLong()), "expense", 10.0) }   // একদিন পরপর
        val weeks = Cycle.blocks(f, es, len = 7)
        assertEquals(6, weeks.size)
        assertEquals(7, weeks.first().t.days)
        assertTrue(weeks.last().partial)
        assertEquals(2, weeks.last().t.days)
        val months = Cycle.blocks(f, es, len = 30)
        assertEquals(2, months.size)
        assertEquals(30, months.first().t.days)
        assertEquals(7, months.last().t.days)
    }

    @Test
    fun `main closed day with expense counts as a date and its expense is included`() {
        val f = folder("main")
        val es = listOf(main("1", d0, 800, 100.0), main("2", d0.plusDays(1), 0, 70.0), main("3", d0.plusDays(2), 400, 0.0))
        val t = Cycle.totals(f, es)
        assertEquals(3, t.days)
        assertEquals(2, t.work)
        assertEquals(1, t.off)
        assertEquals(170.0, t.expense, 0.001)      // বন্ধের দিনের খরচ ৭০ ধরা হয়েছে
        assertEquals(600.0, t.income, 0.001)
    }

    @Test
    fun `categories are grouped and sorted by amount`() {
        val es = listOf(
            money("1", "prem", d0, "expense", 90.0, "ভাত"), money("2", "prem", d0, "expense", 60.0, "নাস্তা"),
            money("3", "prem", d0.plusDays(1), "expense", 40.0, "ভাত"), money("4", "prem", d0, "expense", 10.0, null),
            money("5", "prem", d0, "income", 500.0, "বিক্রি"),
        )
        val ex = Cycle.categoryTotals(es, "expense")
        assertEquals("ভাত", ex[0].first); assertEquals(130.0, ex[0].second, 0.001)
        assertEquals("নাস্তা", ex[1].first)
        assertEquals(Cats.OTHER, ex[2].first)      // খাত ছাড়া এন্ট্রি "অন্যান্য"-এ
        assertEquals(1, Cycle.categoryTotals(es, "income").size)
    }

    @Test
    fun `entries of other folders and modes never leak into totals`() {
        val f = folder("alt")
        val es = listOf(money("1", "alt", d0, "expense", 50.0), money("2", "prem", d0, "expense", 999.0), main("3", d0, 800, 100.0))
        val t = Cycle.totals(f, es)   // শুধু f-alt এর এন্ট্রি
        assertEquals(50.0, t.expense, 0.001)
    }
}
