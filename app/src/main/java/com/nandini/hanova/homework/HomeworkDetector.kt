package com.nandini.hanova.homework

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Offline, rule-based homework spotting.
 * Looks for homework words in the Chinese line (and its English translation as backup),
 * then tries to read a due date ("下週三", "明天", "10月14日", "next Wednesday"...).
 *
 * Honest limit: rules miss some phrasings and can fire on "we will discuss the exam".
 * The UI always lets the user add or delete a task by hand.
 */
object HomeworkDetector {

    data class Detection(val due: LocalDate?)

    private val zhKeys = listOf(
        "作業", "功課", "習題", "練習題", "報告", "繳交", "交上來", "上傳",
        "截止", "期限", "小考", "考試", "期中", "期末", "預習", "複習", "要讀", "閱讀",
    )
    private val enKeys = Regex(
        "\\b(homework|assignment|exercises?|submit|hand in|due|deadline|quiz|exam|report|read\\b.*\\bbefore)\\b",
        RegexOption.IGNORE_CASE,
    )

    private val zhDow = mapOf(
        '一' to DayOfWeek.MONDAY, '二' to DayOfWeek.TUESDAY, '三' to DayOfWeek.WEDNESDAY,
        '四' to DayOfWeek.THURSDAY, '五' to DayOfWeek.FRIDAY, '六' to DayOfWeek.SATURDAY,
        '日' to DayOfWeek.SUNDAY, '天' to DayOfWeek.SUNDAY,
    )
    private val enDow = DayOfWeek.values().associateBy { it.name.lowercase() }

    private val nextWeekZh = Regex("(下週|下周|下星期|下禮拜)([一二三四五六日天])")
    private val thisWeekZh = Regex("(這週|這周|本週|本周|星期|週|周|禮拜)([一二三四五六日天])")
    private val monthDayZh = Regex("(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*[日號号]")
    private val nextWeekEn = Regex("next (monday|tuesday|wednesday|thursday|friday|saturday|sunday)", RegexOption.IGNORE_CASE)
    private val byDayEn = Regex("(by|on|before|this) (monday|tuesday|wednesday|thursday|friday|saturday|sunday)", RegexOption.IGNORE_CASE)

    fun detect(zh: String, en: String, today: LocalDate = LocalDate.now()): Detection? {
        val hit = zhKeys.any { zh.contains(it) } || enKeys.containsMatchIn(en)
        if (!hit) return null
        return Detection(parseZh(zh, today) ?: parseEn(en, today))
    }

    private fun parseZh(s: String, today: LocalDate): LocalDate? {
        if (s.contains("後天")) return today.plusDays(2)
        if (s.contains("明天")) return today.plusDays(1)
        nextWeekZh.find(s)?.let { m ->
            val dow = zhDow[m.groupValues[2][0]] ?: return@let
            val nextMonday = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
            return nextMonday.with(TemporalAdjusters.nextOrSame(dow))
        }
        monthDayZh.find(s)?.let { m ->
            val month = m.groupValues[1].toInt(); val day = m.groupValues[2].toInt()
            if (month in 1..12 && day in 1..31) {
                val d = runCatching { LocalDate.of(today.year, month, day) }.getOrNull() ?: return@let
                return if (d.isBefore(today)) d.plusYears(1) else d
            }
        }
        thisWeekZh.find(s)?.let { m ->
            val dow = zhDow[m.groupValues[2][0]] ?: return@let
            return today.with(TemporalAdjusters.next(dow))
        }
        return null
    }

    private fun parseEn(s: String, today: LocalDate): LocalDate? {
        val low = s.lowercase()
        if (low.contains("day after tomorrow")) return today.plusDays(2)
        if (low.contains("tomorrow")) return today.plusDays(1)
        nextWeekEn.find(low)?.let { m ->
            val dow = enDow[m.groupValues[1]] ?: return@let
            val nextMonday = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
            return nextMonday.with(TemporalAdjusters.nextOrSame(dow))
        }
        byDayEn.find(low)?.let { m ->
            val dow = enDow[m.groupValues[2]] ?: return@let
            return today.with(TemporalAdjusters.next(dow))
        }
        return null
    }
}
