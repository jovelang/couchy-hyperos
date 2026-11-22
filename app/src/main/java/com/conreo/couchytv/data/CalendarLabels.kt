// Modified for Couchy HyperOS, 2026-11-23. Upstream and change notices: UPSTREAM.md, CHANGELOG.md.
package com.conreo.couchytv.data

import android.icu.util.ChineseCalendar
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object CalendarLabels {
    fun solar(date: Date, format: Int, locale: Locale): String {
        if (format == 7) {
            val c = Calendar.getInstance().apply { time = date }
            val weekday = "日一二三四五六"[c.get(Calendar.DAY_OF_WEEK) - 1]
            return "${c.get(Calendar.MONTH) + 1}月${c.get(Calendar.DAY_OF_MONTH)}日 周$weekday"
        }
        val pattern = DATE_FORMATS.getOrElse(format) { "" }
        return if (pattern.isEmpty()) "" else SimpleDateFormat(pattern, locale).format(date)
    }

    fun lunar(date: Date): String {
        val c = ChineseCalendar().apply { timeInMillis = date.time }
        val months = listOf("正", "二", "三", "四", "五", "六", "七", "八", "九", "十", "冬", "腊")
        val days = listOf("初一", "初二", "初三", "初四", "初五", "初六", "初七", "初八", "初九", "初十",
            "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十",
            "廿一", "廿二", "廿三", "廿四", "廿五", "廿六", "廿七", "廿八", "廿九", "三十")
        val leap = if (c.get(ChineseCalendar.IS_LEAP_MONTH) == 1) "闰" else ""
        return "农历 $leap${months[c.get(ChineseCalendar.MONTH)]}月${days[c.get(ChineseCalendar.DAY_OF_MONTH) - 1]}"
    }
}
