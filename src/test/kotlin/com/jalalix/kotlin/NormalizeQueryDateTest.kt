package com.jalalix.kotlin

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class NormalizeQueryDateTest {
	@Test
	fun normalizeQueryDateSentinelsAndTimestamps() {
		assertNull(JalaliX.normalizeQueryDate(null))
		assertNull(JalaliX.normalizeQueryDate("null"))
		assertNull(JalaliX.normalizeQueryDate(""))
		assertNull(JalaliX.normalizeQueryDate("undefined"))
		assertNull(JalaliX.normalizeQueryDate("Invalid Date"))
		assertNull(JalaliX.normalizeQueryDate("NaN"))
		assertNull(JalaliX.normalizeQueryDate(0))
		assertNull(JalaliX.normalizeQueryDate(-1))

		val seconds = 1740430800L
		assertEquals(
			"2025-02-24 21:00:00",
			JalaliX.normalizeQueryDate(seconds, JalaliQueryOptions.ofTimezone("UTC")),
		)
		assertEquals(
			"2025-02-25 00:30:00",
			JalaliX.normalizeQueryDate(seconds * 1000, JalaliQueryOptions.ofTimezone("Asia/Tehran")),
		)
	}

	@Test
	fun normalizeQueryDateJalaliAndGregorian() {
		assertEquals(
			"2025-02-25 00:00:00",
			JalaliX.normalizeQueryDate("1403/12/07", JalaliQueryOptions.ofTimezone("UTC")),
		)
		assertEquals(
			"2025-02-25 14:30:00",
			JalaliX.normalizeQueryDate("۱۴۰۳-۱۲-۰۷ 14:30:00", JalaliQueryOptions.ofTimezone("UTC")),
		)
		assertEquals("2025-02-25 00:00:00", JalaliX.normalizeQueryDate("2025.02.25"))
		assertEquals("2025-02-25 12:30:45", JalaliX.normalizeQueryDate("2025-02-25 12:30:45.123Z"))
		assertEquals("not-a-date", JalaliX.normalizeQueryDate("not-a-date"))

		assertNotNull(JalaliX.normalizeQueryDate("1200/01/01", JalaliQueryOptions.ofTimezone("UTC")))
		assertNotNull(JalaliX.normalizeQueryDate("1800/01/01", JalaliQueryOptions.ofTimezone("UTC")))

		assertEquals("2025-01-01 00:00:00", JalaliX.normalizeQueryDate("2025-01-01"))
		val widened = JalaliQueryOptions(timezone = "UTC", jalaliYearMin = 1, jalaliYearMax = 3000)
		assertNotEquals("2025-01-01 00:00:00", JalaliX.normalizeQueryDate("2025-01-01", widened))
	}

	@Test
	fun fromPersianArabicIndicAndParseDot() {
		assertEquals("1403/12/07", JalaliX.fromPersian("١٤٠٣/١٢/٠٧"))
		assertEquals("2025-02-25", JalaliX.parseJalali("1403.12.07").toISODate())
	}
}
