package com.smarthisab.app.domain

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * ক্যালকুলেটরের হিসাব। ভেতরে লেখা ASCII সংখ্যা ও + − × ÷ % . দিয়ে চলে; দেখানোর সময় বাংলা সংখ্যায়।
 * নিয়ম: × ÷ আগে, তারপর + −। "২০০ + ১০%" মানে ২০০ এর ১০% যোগ (২২০); একা "৫০%" = ০.৫।
 */
object CalcEngine {
    const val OPS = "+−×÷"
    private const val BN = "০১২৩৪৫৬৭৮৯"

    fun norm(s: String): String = buildString {
        for (c in s) append(
            when {
                c in BN -> '0' + BN.indexOf(c)
                c == '-' -> '−'
                c == '*' || c == 'x' || c == 'X' -> '×'
                c == '/' -> '÷'
                else -> c
            },
        )
    }

    /** শেষে ঝুলে থাকা অপারেটর বাদ দিয়ে মান; ভুল বা ০ দিয়ে ভাগ হলে null */
    fun eval(raw: String): Double? {
        val s = norm(raw).trimEnd('+', '−', '×', '÷', '.')
        if (s.none { it.isDigit() }) return null
        val nums = ArrayList<Pair<Double, Boolean>>()   // (মান, শতাংশ কিনা)
        val ops = ArrayList<Char>()
        var cur = StringBuilder()
        var pct = false
        fun push(): Boolean {
            val v = cur.toString().toDoubleOrNull() ?: return false
            nums.add(v to pct); cur = StringBuilder(); pct = false
            return true
        }
        var i = 0
        if (s.startsWith("−")) { cur.append('-'); i = 1 }
        while (i < s.length) {
            val c = s[i]
            when {
                c.isDigit() || c == '.' -> cur.append(c)
                c == '%' -> pct = true
                c in OPS -> { if (!push()) return null; ops.add(c) }
                else -> return null
            }
            i++
        }
        if (!push()) return null

        // ধাপ ১: × ও ÷
        val terms = ArrayList<Pair<Double, Boolean>>()   // (মান, একক শতাংশ কিনা)
        val addOps = ArrayList<Char>()
        fun v(p: Pair<Double, Boolean>) = if (p.second) p.first / 100.0 else p.first
        var acc = v(nums[0])
        var single = nums[0].second
        for (k in ops.indices) {
            val n = nums[k + 1]
            when (ops[k]) {
                '×' -> { acc *= v(n); single = false }
                '÷' -> { val d = v(n); if (d == 0.0) return null; acc /= d; single = false }
                else -> { terms.add(acc to single); addOps.add(ops[k]); acc = v(n); single = n.second }
            }
        }
        terms.add(acc to single)
        // ধাপ ২: + ও −
        var total = terms[0].first
        for (k in addOps.indices) {
            val (x, isPct) = terms[k + 1]
            val delta = if (isPct) total * x else x
            total = if (addOps[k] == '+') total + delta else total - delta
        }
        return total
    }

    private fun plainString(d: Double): String =
        BigDecimal(d).setScale(8, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

    /** ASCII লেখা, কমা ছাড়া — ফল থেকে নতুন হিসাব চালানোর জন্য */
    fun plain(d: Double): String = plainString(d).let { if (it.startsWith("-")) "−" + it.drop(1) else it }

    /** দেখানোর লেখা: বাংলা সংখ্যা ও কমা (১,০১০ / ০.৩) */
    fun format(d: Double): String {
        if (d.isNaN() || d.isInfinite()) return "ত্রুটি"
        val p = plainString(d)
        val neg = p.startsWith("-")
        val body = p.removePrefix("-")
        val parts = body.split('.')
        val grouped = parts[0].reversed().chunked(3).joinToString(",").reversed()
        val text = grouped + if (parts.size > 1) "." + parts[1] else ""
        return (if (neg && d != 0.0) "−" else "") + Fmt.bn(text)
    }
}
