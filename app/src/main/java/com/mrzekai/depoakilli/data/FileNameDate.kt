package com.mrzekai.depoakilli.data

import java.time.DateTimeException
import java.time.LocalDate
import java.time.ZoneId

/**
 * Capture date written in a file name by cameras and messengers
 * (IMG_20240105_101500.jpg, VID-20230611-WA0003.mp4, Screenshot_2024-03-02-…,
 * PXL_20231224_…). A copied or restored file keeps this date even when the
 * file system gives it a new modification time (V1).
 */
object FileNameDate {
    private const val MIN_YEAR = 2005
    private val CANDIDATE = Regex("""(?<!\d)(\d{4})([-_]?)(\d{2})\2(\d{2})""")

    /**
     * Start of the first valid date in [name] (local time zone), or null.
     * The year must lie between 2005 and the current year; a future date is
     * rejected.
     */
    fun parse(
        name: String,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long? {
        val today = java.time.Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        for (match in CANDIDATE.findAll(name)) {
            val year = match.groupValues[1].toInt()
            val month = match.groupValues[3].toInt()
            val day = match.groupValues[4].toInt()
            if (year < MIN_YEAR || year > today.year || month !in 1..12 || day !in 1..31) continue
            val date = try {
                LocalDate.of(year, month, day)
            } catch (invalid: DateTimeException) {
                continue
            }
            if (date.isAfter(today)) continue
            return date.atStartOfDay(zone).toInstant().toEpochMilli()
        }
        return null
    }
}
