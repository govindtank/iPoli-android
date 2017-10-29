package io.ipoli.android.common.text

import android.content.Context
import io.ipoli.android.R
import io.ipoli.android.common.datetime.*
import org.threeten.bp.LocalDate
import java.text.SimpleDateFormat
import java.util.*

/**
 * Created by Venelin Valkov <venelin@ipoli.io>
 * on 10/21/17.
 */
class CalendarFormatter(private val context: Context) {

    fun date(date: LocalDate): String =
        formatter(date.dayOfMonth)
            .format(date.toStartOfDayUTC())

    private fun formatter(dayOfMonth: Int) =
//        SimpleDateFormat(pattern(dayOfMonth), Locale.US)
        SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private fun pattern(day: Int): String {
        if (day !in 11..18)
            return when (day % 10) {
                1 -> "MMM d'st' YY"
                2 -> "MMM d'nd' YY"
                3 -> "MMM d'rd' YY"
                else -> "MMM d'th' YY"
            }
        return "MMM d'th' YY"
    }

    fun day(date: LocalDate): String {
        return when {
            date.isToday -> context.getString(R.string.today)
            date.isTomorrow -> context.getString(R.string.tomorrow)
            date.isYesterday -> context.getString(R.string.yesterday)
            else -> date.dayOfWeekText
        }
    }
}