package com.smarthisab.app

import com.smarthisab.app.data.EntryE
import com.smarthisab.app.data.FolderE
import com.smarthisab.app.domain.Cycle
import com.smarthisab.app.domain.DraftRow
import com.smarthisab.app.domain.planDaySave
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** আয়-ব্যয়: একই তারিখের একাধিক আয় ও খরচের সারি একসাথে সংরক্ষণ */
class DayPlanTest {
    private val folder = FolderE(id = "p", name = "p", startDay = LocalDate.of(2026, 10, 1).toEpochDay(), wageRate = 50.0, mode = "prem")
    private val day = LocalDate.of(2026, 10, 30)

    private fun saved(id: String, kind: String, cat: String, amount: Double, version: Int = 3) = EntryE(
        id = id, folderId = "p", day = day.toEpochDay(), quantity = 0, expense = 0.0, rate = 0.0,
        mode = "prem", kind = kind, category = cat, amount = amount, version = version, dirty = false,
    )

    private fun apply(existing: List<EntryE>, plan: com.smarthisab.app.domain.DayPlan): List<EntryE> {
        val map = existing.associateBy { it.id }.toMutableMap()
        (plan.upserts + plan.deletes).forEach { map[it.id] = it }
        return map.values.filter { it.deletedAt == null }
    }

    @Test
    fun `new day saves every income and expense row separately`() {
        val drafts = listOf(
            DraftRow("i1", "income", "বিক্রি", 500.0), DraftRow("i2", "income", "মজুরি", 100.0),
            DraftRow("e1", "expense", "নাস্তা", 40.0), DraftRow("e2", "expense", "ভাত", 80.0), DraftRow("e3", "expense", "গাড়ি ভাড়া", 30.0),
        )
        val plan = planDaySave(folder, day, emptyList(), drafts, 1000L)
        assertEquals(5, plan.upserts.size)
        assertTrue(plan.deletes.isEmpty())
        val t = Cycle.totals(folder, plan.upserts)
        assertEquals(600.0, t.income, 0.001)
        assertEquals(150.0, t.expense, 0.001)
        assertEquals(1, t.days)                       // এক তারিখ = এক দিন
        val cats = Cycle.categoryTotals(plan.upserts, "expense").map { it.first }.toSet()
        assertEquals(setOf("নাস্তা", "ভাত", "গাড়ি ভাড়া"), cats)   // খাত আলাদা থাকে
    }

    @Test
    fun `saving the same form twice creates no duplicates`() {
        val drafts = listOf(DraftRow("i1", "income", "বিক্রি", 500.0), DraftRow("e1", "expense", "ভাত", 80.0))
        val first = planDaySave(folder, day, emptyList(), drafts, 1000L)
        val stored = apply(emptyList(), first)
        val second = planDaySave(folder, day, stored, drafts, 2000L)
        assertTrue(second.upserts.isEmpty() && second.deletes.isEmpty())   // কিছুই বদলায়নি
        val doubleSubmit = planDaySave(folder, day, emptyList(), drafts + drafts, 3000L)
        assertEquals(2, doubleSubmit.upserts.size)                          // একই id দুবার এলেও একবার
    }

    @Test
    fun `editing one row updates only that row and keeps its version`() {
        val existing = listOf(saved("i1", "income", "বিক্রি", 500.0), saved("e1", "expense", "ভাত", 80.0), saved("e2", "expense", "নাস্তা", 40.0))
        val drafts = listOf(DraftRow("i1", "income", "বিক্রি", 500.0), DraftRow("e1", "expense", "ভাত", 90.0), DraftRow("e2", "expense", "নাস্তা", 40.0))
        val plan = planDaySave(folder, day, existing, drafts, 5000L)
        assertEquals(1, plan.upserts.size)
        assertEquals("e1", plan.upserts[0].id)
        assertEquals(90.0, plan.upserts[0].amount, 0.001)
        assertEquals(3, plan.upserts[0].version)       // সংস্করণ ধরে রাখা (সিঙ্কের সংঘর্ষ-পরীক্ষার জন্য)
        assertTrue(plan.upserts[0].dirty)
        assertTrue(plan.deletes.isEmpty())
        val t = Cycle.totals(folder, apply(existing, plan))
        assertEquals(130.0, t.expense, 0.001)          // ৯০ + ৪০
    }

    @Test
    fun `removed or zero rows are soft deleted and totals follow`() {
        val existing = listOf(saved("i1", "income", "বিক্রি", 500.0), saved("e1", "expense", "ভাত", 80.0), saved("e2", "expense", "নাস্তা", 40.0))
        val drafts = listOf(DraftRow("i1", "income", "বিক্রি", 500.0), DraftRow("e2", "expense", "নাস্তা", 0.0))   // e1 ফর্মে নেই, e2 পরিমাণ ০
        val plan = planDaySave(folder, day, existing, drafts, 7000L)
        assertEquals(setOf("e1", "e2"), plan.deletes.map { it.id }.toSet())
        assertTrue(plan.deletes.all { it.deletedAt == 7000L && it.dirty })
        val t = Cycle.totals(folder, apply(existing, plan))
        assertEquals(500.0, t.income, 0.001)
        assertEquals(0.0, t.expense, 0.001)
    }

    @Test
    fun `custom category and kind are kept and other days are untouched`() {
        val other = saved("x", "expense", "ভাত", 10.0).copy(day = day.plusDays(1).toEpochDay())
        val plan = planDaySave(folder, day, listOf(other), listOf(DraftRow("n1", "expense", "চা", 15.0)), 1L)
        assertEquals("চা", plan.upserts[0].category)
        assertTrue(plan.deletes.isEmpty())            // অন্য তারিখের এন্ট্রি মোছা হয় না
    }
}
