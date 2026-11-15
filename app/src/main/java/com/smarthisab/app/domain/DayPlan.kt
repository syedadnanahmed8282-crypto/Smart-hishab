package com.smarthisab.app.domain

import com.smarthisab.app.data.EntryE
import com.smarthisab.app.data.FolderE
import java.time.LocalDate

/** আয়-ব্যয়ের দৈনিক ফর্মের এক সারি। id ফর্মে সারি বানানোর সময়ই ঠিক হয়, তাই দুবার সংরক্ষণেও ডুপ্লিকেট হয় না। */
data class DraftRow(val id: String, val kind: String, val category: String?, val amount: Double, val note: String? = null)

/** একটা তারিখ সংরক্ষণে যা করতে হবে: যোগ/বদল (upserts) আর মুছে ফেলা (deletes, soft delete) */
data class DayPlan(val upserts: List<EntryE>, val deletes: List<EntryE>)

/**
 * ফর্মের সারির সাথে ডাটাবেসের ওই তারিখের এন্ট্রি মিলিয়ে পরিকল্পনা বানায়।
 * - একই id আগে থাকলে শুধু বদলালে আপডেট (সংস্করণ ধরে রাখা হয়), না বদলালে কিছুই না
 * - নতুন id হলে নতুন এন্ট্রি
 * - ফর্মে নেই (বা পরিমাণ ০) এমন আগের এন্ট্রি মুছে যায়
 * - একই id দুবার এলে একবারই ধরা হয়
 */
fun planDaySave(folder: FolderE, day: LocalDate, existing: List<EntryE>, drafts: List<DraftRow>, now: Long): DayPlan {
    val ex = existing.filter { it.deletedAt == null && it.day == day.toEpochDay() }.associateBy { it.id }
    val seen = LinkedHashSet<String>()
    val ups = mutableListOf<EntryE>()
    for (d in drafts) {
        if (d.amount <= 0.0 || !seen.add(d.id)) continue
        val old = ex[d.id]
        val kind = if (folder.mode == "alt" || d.kind != "income") "expense" else "income"
        val cat = d.category?.trim()?.takeIf { it.isNotEmpty() }
        val note = d.note?.trim()?.takeIf { it.isNotEmpty() }
        val e = EntryE(
            id = d.id, folderId = folder.id, day = day.toEpochDay(), quantity = 0, expense = 0.0, rate = 0.0, note = note,
            deletedAt = null, updatedAt = now, dirty = true, version = old?.version ?: 0,
            mode = folder.mode, kind = kind, category = cat, amount = d.amount,
        )
        if (old == null || old.kind != e.kind || old.category != e.category || old.amount != e.amount || old.note != e.note) ups += e
    }
    val dels = ex.values.filter { it.id !in seen }.map { it.copy(deletedAt = now, updatedAt = now, dirty = true) }
    return DayPlan(ups, dels)
}
