package com.japaneixxx.odin.ui.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateFormatter {

    fun formatRelativeDate(timestamp: Long): String {
        val noteCalendar = Calendar.getInstance().apply { timeInMillis = timestamp }
        val nowCalendar = Calendar.getInstance()

        val isToday = nowCalendar.get(Calendar.YEAR) == noteCalendar.get(Calendar.YEAR) &&
                nowCalendar.get(Calendar.DAY_OF_YEAR) == noteCalendar.get(Calendar.DAY_OF_YEAR)

        val timeFormat = SimpleDateFormat("HH:mm", Locale("pt", "BR"))

        return if (isToday) {
            "Hoje, ${timeFormat.format(Date(timestamp))}"
        } else {
            val dateFormat = SimpleDateFormat("dd 'de' MMM, HH:mm", Locale("pt", "BR"))
            dateFormat.format(Date(timestamp))
        }
    }
}