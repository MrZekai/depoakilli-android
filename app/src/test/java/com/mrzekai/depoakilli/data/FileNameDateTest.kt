package com.mrzekai.depoakilli.data

import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FileNameDateTest {
    private val zone = ZoneOffset.UTC
    private val now = LocalDate.of(2026, 11, 17).atStartOfDay(zone).toInstant().toEpochMilli() + 3_600_000L

    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atStartOfDay(zone).toInstant().toEpochMilli()
    private fun parse(name: String) = FileNameDate.parse(name, now, zone)

    @Test
    fun `camera and messenger names give their date`() {
        assertEquals(day(2024, 1, 5), parse("IMG_20240105_101500.jpg"))
        assertEquals(day(2023, 6, 11), parse("VID-20230611-WA0003.mp4"))
        assertEquals(day(2024, 3, 2), parse("Screenshot_2024-03-02-10-11-12.png"))
        assertEquals(day(2023, 12, 24), parse("PXL_20231224_183000123.jpg"))
        assertEquals(day(2022, 7, 9), parse("Recording_2022_07_09.m4a"))
    }

    @Test
    fun `invalid future and random numbers give null`() {
        assertNull(parse("12345678.jpg"))
        assertNull(parse("IMG_20241332_1.jpg"))
        assertNull(parse("IMG_20230230_1.jpg"))
        assertNull(parse("IMG_20271201_1.jpg"))
        assertNull(parse("IMG_20261118_1.jpg"))
        assertNull(parse("IMG_20041231_1.jpg"))
        assertNull(parse("holiday.jpg"))
        assertNull(parse("2024-01_05.jpg"))
    }

    @Test
    fun `the first valid date wins`() {
        assertEquals(day(2021, 2, 3), parse("99999999_IMG_20210203_20220101.jpg"))
        assertEquals(day(2026, 11, 17), parse("IMG_20261117_0900.jpg"))
    }
}
