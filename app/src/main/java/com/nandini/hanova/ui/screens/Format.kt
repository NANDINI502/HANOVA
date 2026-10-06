package com.nandini.hanova.ui.screens

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal object Fmt {
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val dayLong = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)
    private val dayShort = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)
    private val time = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

    fun date(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    fun todayLong(): String = LocalDate.now().format(dayLong)
    fun dayHeader(d: LocalDate): String = when (d) {
        LocalDate.now() -> "Today"
        LocalDate.now().minusDays(1) -> "Yesterday"
        else -> d.format(dayLong)
    }
    fun shortDay(d: LocalDate): String = d.format(dayShort)
    fun clock(millis: Long): String = Instant.ofEpochMilli(millis).atZone(zone).format(time)

    /** 42:18 or 1:02:05 */
    fun elapsed(ms: Long): String {
        val s = ms / 1000; val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
    }

    /** 1 h 48 m / 50 m */
    fun duration(ms: Long): String {
        val totalMin = ms / 60000; val h = totalMin / 60; val m = totalMin % 60
        return if (h > 0) "$h h %02d m".format(m) else "$m m"
    }

    fun due(epochDay: Long?): String {
        epochDay ?: return "No due date"
        val d = LocalDate.ofEpochDay(epochDay)
        val today = LocalDate.now()
        return when {
            d == today -> "Due today"
            d == today.plusDays(1) -> "Due tomorrow"
            d.isBefore(today) -> "Overdue · ${shortDay(d)}"
            else -> "Due ${shortDay(d)}"
        }
    }

    fun isUrgent(epochDay: Long?): Boolean =
        epochDay != null && !LocalDate.ofEpochDay(epochDay).isAfter(LocalDate.now().plusDays(2))
}
