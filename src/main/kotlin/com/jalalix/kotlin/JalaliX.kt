package com.jalalix.kotlin

import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeParseException
import java.time.temporal.TemporalAccessor
import java.util.Locale
import java.util.regex.Pattern
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Jalali (Persian / Shamsi) calendar on a UTC millisecond instant + IANA [ZoneId].
 * Pure Kotlin / java.time port of @jalalix/typescript — same algorithm and camelCase API.
 */
class JalaliX : Cloneable {
	companion object {
		@JvmField val persianNumbers = arrayOf("۰", "۱", "۲", "۳", "۴", "۵", "۶", "۷", "۸", "۹")

		private const val LEAP_YEAR_DIVISION = 0.24219858156
		private const val FIRST_DAY_OF_WEEK = 6
		private const val DATE_LENGTH = 86_400_000L

		private val JALALI_PARSE = Pattern.compile(
			"^(\\d{4})[/-](\\d{1,2})[/-](\\d{1,2})(?:[ T](\\d{1,2}):(\\d{1,2})(?::(\\d{1,2}))?)?$"
		)

		private val DATE_ONLY = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$")

		private val WEEK_DAYS_NAMES = arrayOf("شنبه", "یکشنبه", "دوشنبه", "سه شنبه", "چهار شنبه", "پنج شنبه", "جمعه")
		private val WEEK_DAYS_LETTERS = arrayOf("ش", "ی", "د", "س", "چ", "پ", "ج")

		private val WEEK_DAYS_GREGORIAN_NAMES = arrayOf(
			"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"
		)

		private val WEEK_DAYS_GREGORIAN_LETTERS = arrayOf("S", "M", "T", "W", "T", "F", "S")

		private val MONTHS_NAMES = arrayOf(
			"فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
			"مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
		)

		private val MONTHS_GREGORIAN_NAMES = arrayOf(
			"January", "February", "March", "April", "May", "June",
			"July", "August", "September", "October", "November", "December"
		)

		private fun hostTimezone(): String = ZoneId.systemDefault().id

		private fun dateUtc(y: Int, m: Int, d: Int, h: Int, mi: Int, s: Int): Long =
			LocalDateTime.of(y, m, d, h, mi, s).toInstant(ZoneOffset.UTC).toEpochMilli()

		private fun parseIsoString(value: String): Long {
			val s = value.trim()

			if (DATE_ONLY.matcher(s).matches()) {
				return LocalDate.parse(s).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
			}

			try {
				if (s.endsWith("Z", true)) return Instant.parse(s).toEpochMilli()

				if (s.contains("T")) {
					try { return Instant.parse(s).toEpochMilli() } catch (_: DateTimeParseException) {}

					try {
						return java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(s, Instant::from).toEpochMilli()
					} catch (_: DateTimeParseException) {}

					val ldt = LocalDateTime.parse(s)

					return ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
				}

				return Instant.parse(s).toEpochMilli()
			} catch (e: DateTimeParseException) {
				throw IllegalArgumentException("JalaliX.create: invalid date string \"$value\"", e)
			}
		}

		private fun parseInput(date: Any): Long = when (date) {
			is Number -> date.toLong()
			is JalaliX -> date.getTime()
			is Instant -> date.toEpochMilli()
			is TemporalAccessor -> Instant.from(date).toEpochMilli()
			is String -> parseIsoString(date)
			else -> throw IllegalArgumentException("JalaliX.create: unsupported date type")
		}

		@JvmStatic @JvmOverloads
		fun create(date: Any, options: JalaliOptions? = null): JalaliX {
			var output = JalaliX(parseInput(date))

			if (options?.timezone != null) output = output.setTimezone(options.timezone)

			return output
		}

		@JvmStatic @JvmOverloads
		fun parseJalali(value: String, options: JalaliOptions? = null): JalaliX {
			val normalized = fromPersian(value).trim()
			val match = JALALI_PARSE.matcher(normalized)

			if (!match.matches()) throw IllegalArgumentException("JalaliX.parseJalali: invalid Jalali date \"$value\"")

			val year = match.group(1).toInt()
			val month = match.group(2).toInt()
			val day = match.group(3).toInt()
			val hours = match.group(4)?.toInt() ?: 0
			val minutes = match.group(5)?.toInt() ?: 0
			val seconds = match.group(6)?.toInt() ?: 0

			if (month !in 1..12 || day !in 1..31) {
				throw IllegalArgumentException("JalaliX.parseJalali: invalid Jalali date \"$value\"")
			}

			val output = create(System.currentTimeMillis(), options)
			output.setFullYear(year, month - 1, day)
			output.setHours(hours, minutes, seconds, 0)

			return output
		}

		@JvmStatic
		fun isValidDate(date: Any?): Boolean = when (date) {
			is JalaliX, is Instant, is Number -> true
			is TemporalAccessor -> try { Instant.from(date); true } catch (_: Exception) { false }
			else -> false
		}

		@JvmStatic
		fun toPersian(str: String): String {
			var out = str

			for (i in 0..9) out = out.replace(i.toString(), persianNumbers[i])

			return out
		}

		@JvmStatic
		fun fromPersian(str: String): String {
			var out = str

			for (i in 0..9) out = out.replace(persianNumbers[i], i.toString())

			return out
		}

		@JvmStatic
		fun compare(date: JalaliX, comparing: JalaliX): Boolean = date.getTime() < comparing.getTime()
	}

	private class WallParts {
		var year = 0; var month = 0; var day = 0
		var hour = 0; var minute = 0; var second = 0; var ms = 0
	}

	private class JalaliDate {
		var year = 1348; var month = 10; var day = 11
		var hours = 0; var min = 0; var sec = 0; var ms = 0
		var isLeapYear = false
	}

	private var time: Long
	private var timezone: String = "UTC"
	private val jalali = JalaliDate()
	private var isConverted = false
	private val am = "ق.ظ."
	private val pm = "ب.ظ."
	private val amGregorian = "AM"
	private val pmGregorian = "PM"

	constructor() { time = System.currentTimeMillis() }
	constructor(epochMs: Long) { time = epochMs }
	constructor(isoOrDate: String) { time = parseInput(isoOrDate) }
	constructor(instant: Instant) { time = instant.toEpochMilli() }
	constructor(temporal: TemporalAccessor) { time = Instant.from(temporal).toEpochMilli() }

	private fun zoneId(): ZoneId = ZoneId.of(timezone)

	private fun getWallParts(): WallParts {
		val z = Instant.ofEpochMilli(time).atZone(zoneId())

		return WallParts().also {
			it.year = z.year; it.month = z.monthValue; it.day = z.dayOfMonth
			it.hour = z.hour; it.minute = z.minute; it.second = z.second
			it.ms = Math.floorMod(time, 1000)
		}
	}

	private fun wallToUtc(y: Int, m: Int, d: Int, h: Int, mi: Int, s: Int, ms: Int): Long {
		val want = dateUtc(y, m, d, h, mi, s)
		var utc = want
		val zone = zoneId()

		for (i in 0 until 14) {
			val parts = Instant.ofEpochMilli(utc).atZone(zone)
			val got = dateUtc(parts.year, parts.monthValue, parts.dayOfMonth, parts.hour, parts.minute, parts.second)
			val diff = want - got
			if (diff == 0L) break
			utc += diff
		}

		return (utc / 1000) * 1000 + Math.floorMod(ms, 1000)
	}

	private fun toJalali(): JalaliDate {
		if (!isConverted) {
			val wall = getWallParts()
			val civilDay = dateUtc(wall.year, wall.month, wall.day, 0, 0, 0) / DATE_LENGTH
			val baseDay = dateUtc(1970, 1, 1, 0, 0, 0) / DATE_LENGTH
			val diffDays = civilDay - baseDay
			jalali.year = 1348; jalali.month = 10; jalali.day = 11

			if (diffDays > 0) {
				for (i in 0 until diffDays) {
					jalali.day++

					when {
						jalali.month < 7 && jalali.day > 31 -> { jalali.month++; jalali.day = 1 }
						jalali.month in 7..11 && jalali.day > 30 -> { jalali.month++; jalali.day = 1 }
						jalali.month == 12 -> {
							val leap = checkLeapYear(jalali.year)

							if ((leap && jalali.day > 30) || (!leap && jalali.day > 29)) {
								jalali.year++; jalali.month = 1; jalali.day = 1
							}
						}
					}
				}
			} else if (diffDays < 0) {
				for (i in 0 until -diffDays) {
					jalali.day--

					if (jalali.day < 1) {
						when {
							jalali.month == 1 -> {
								jalali.year--; jalali.month = 12
								jalali.day = if (checkLeapYear(jalali.year)) 30 else 29
							}

							jalali.month < 8 -> { jalali.month--; jalali.day = 31 }
							jalali.month < 13 -> { jalali.month--; jalali.day = 30 }
						}
					}
				}
			}

			jalali.hours = wall.hour; jalali.min = wall.minute; jalali.sec = wall.second; jalali.ms = wall.ms
			jalali.isLeapYear = checkLeapYear(jalali.year)
			isConverted = true
		}

		return jalali
	}

	private fun toGregorian(jDate: JalaliDate): Long {
		val cur = JalaliDate()
		var diffDays = 0L

		val after = jDate.year > 1348 || (jDate.year == 1348 && jDate.month >= 10) ||
			(jDate.year == 1348 && jDate.month == 10 && jDate.day >= 11)

		val before = jDate.year < 1348 || (jDate.year == 1348 && jDate.month < 10) ||
			(jDate.year == 1348 && jDate.month == 10 && jDate.day < 11)

		if (after) {
			while (!(cur.year == jDate.year && cur.month == jDate.month && cur.day == jDate.day)) {
				diffDays++; cur.day++

				when {
					cur.month < 7 && cur.day > 31 -> { cur.month++; cur.day = 1 }
					cur.month in 7..11 && cur.day > 30 -> { cur.month++; cur.day = 1 }
					cur.month == 12 -> {
						val leap = checkLeapYear(cur.year)

						if ((leap && cur.day > 30) || (!leap && cur.day > 29)) {
							cur.year++; cur.month = 1; cur.day = 1
						}
					}
				}
			}
		} else if (before) {
			while (!(cur.year == jDate.year && cur.month == jDate.month && cur.day == jDate.day)) {
				diffDays--; cur.day--

				if (cur.day < 1) {
					when {
						cur.month == 1 -> {
							cur.year--; cur.month = 12
							cur.day = if (checkLeapYear(cur.year)) 30 else 29
						}

						cur.month < 8 -> { cur.month--; cur.day = 31 }
						cur.month < 13 -> { cur.month--; cur.day = 30 }
					}
				}
			}
		}

		val wallAsUtc = diffDays * DATE_LENGTH +
			(jDate.hours * 3600L + jDate.min * 60L + jDate.sec) * 1000L + jDate.ms

			val asUtc = Instant.ofEpochMilli(wallAsUtc).atZone(ZoneOffset.UTC)

			return wallToUtc(asUtc.year, asUtc.monthValue, asUtc.dayOfMonth, asUtc.hour, asUtc.minute, asUtc.second, asUtc.nano / 1_000_000)
	}

	private fun checkLeapYear(year: Int): Boolean {
		if (year > 0) {
			val checkYear = (year + 2346) * LEAP_YEAR_DIVISION
			return checkYear - checkYear.toLong() < LEAP_YEAR_DIVISION
		}

		return false
	}

	private fun reset(): JalaliDate {
		isConverted = false
		jalali.year = 1348; jalali.month = 10; jalali.day = 11

		return toJalali()
	}

	private fun addZero(number: Int) = (if (number in 0..9) "0" else "") + number
	private fun getJalaliMonthName(number: Int) = MONTHS_NAMES[number - 1]
	private fun getGregorianMonthName(number: Int) = MONTHS_GREGORIAN_NAMES[number - 1]

	private fun getWallWeekday(): Int =
		Instant.ofEpochMilli(dateUtc(getWallParts().year, getWallParts().month, getWallParts().day, 0, 0, 0))
			.atZone(ZoneOffset.UTC).dayOfWeek.value % 7

	private fun getGregorianDayOfYear(): Int {
		val wall = getWallParts()
		val start = dateUtc(wall.year, 1, 1, 0, 0, 0) - DATE_LENGTH
		val civil = dateUtc(wall.year, wall.month, wall.day, 0, 0, 0)

		return Math.round((civil - start).toDouble() / DATE_LENGTH).toInt()
	}

	private fun getGregorianWeekOfYear(): Int {
		val wall = getWallParts()
		var d = Instant.ofEpochMilli(dateUtc(wall.year, wall.month, wall.day, 0, 0, 0)).atZone(ZoneOffset.UTC)
		val utcDay = d.dayOfWeek.value % 7
		val adjust = 4 - (if (utcDay == 0) 7 else utcDay)
		d = d.plusDays(adjust.toLong())
		val yearStart = LocalDate.of(d.year, 1, 1).atStartOfDay(ZoneOffset.UTC)

		return ceil(((d.toInstant().toEpochMilli() - yearStart.toInstant().toEpochMilli()).toDouble() / DATE_LENGTH + 1) / 7.0).toInt()
	}

	private fun getGregorianWeekDayName() = WEEK_DAYS_GREGORIAN_NAMES[getWallWeekday()]
	private fun getGregorianWeekDayLetter() = WEEK_DAYS_GREGORIAN_LETTERS[getWallWeekday()]
	private fun getWeekDayName() = WEEK_DAYS_NAMES[(getDay() + FIRST_DAY_OF_WEEK) % 7]
	private fun getWeekDayLetter() = WEEK_DAYS_LETTERS[(getDay() + FIRST_DAY_OF_WEEK) % 7]

	private fun getUTCTimezone(): String {
		val wall = getWallParts()
		val asUtc = dateUtc(wall.year, wall.month, wall.day, wall.hour, wall.minute, wall.second) + wall.ms
		val offsetMin = Math.round((asUtc - time) / 60000.0).toInt()
		val sign = if (offsetMin >= 0) "+" else "-"
		val abs = kotlin.math.abs(offsetMin)

		return " GMT$sign${addZero(abs / 60)}${addZero(abs % 60)}"
	}

	private fun normalizeDate(days: Int): Long {
		time += days * DATE_LENGTH

		return time
	}

	private fun normalizeJalali(jDate: JalaliDate, yearIn: Int, monthIn: Int, dayIn: Int): Long {
		var year = yearIn; var month = monthIn; var day = dayIn

		if (month < 1) {
			val yearDiff = ceil((1 - month) / 12.0).toInt()
			year -= yearDiff; month = 12 * yearDiff + month
		} else if (month > 12) {
			val yearDiff = floor((month - 1) / 12.0).toInt()
			year += yearDiff; month -= 12 * yearDiff
		}

		if (month in 7..11 && day > 30) day = 30
		else if (month == 12) {
			val leap = checkLeapYear(year)
			if (leap && day > 30) day = 30
			else if (!leap && day > 29) day = 29
		}

		val next = JalaliDate().also {
			it.year = year; it.month = month; it.day = day
			it.hours = jDate.hours; it.min = jDate.min; it.sec = jDate.sec; it.ms = jDate.ms
		}

		time = toGregorian(next)
		return time
	}

	private fun normalizeTimezone(tz: String?): String {
		if (tz == null) return timezone

		val lowered = tz.lowercase(Locale.ROOT)

		when (lowered) {
			"default", "local", "system" -> return hostTimezone()
			"utc", "gmt" -> return "UTC"
		}

		try {
			return ZoneId.of(tz).id
		} catch (e: DateTimeException) {
			throw IllegalArgumentException("Invalid IANA timezone: $tz", e)
		}
	}

	override fun toString(): String {
		val j = toJalali()

		return "${getWeekDayName()} ${addZero(j.day)} ${getJalaliMonthName(j.month)} ${addZero(j.year)} ${j.hours}:${addZero(j.min)}:${addZero(j.sec)}${getUTCTimezone()}"
	}

	fun toDateString(): String {
		val j = toJalali()

		return "${getWeekDayName()} ${addZero(j.day)} ${getJalaliMonthName(j.month)} ${addZero(j.year)}"
	}

	fun toLocaleDateString(): String {
		val j = toJalali()

		return "${addZero(j.year)}/${addZero(j.month)}/${addZero(j.day)}"
	}

	fun toLocaleTimeString(): String {
		val j = toJalali()

		return "${j.hours % 12}:${addZero(j.min)}:${addZero(j.sec)} ${getMeridiem()}"
	}

	fun getFullYear() = toJalali().year
	fun getMonth() = toJalali().month
	fun getDate() = toJalali().day
	fun getDay() = (getWallWeekday() - FIRST_DAY_OF_WEEK + 7 + 1) % 7
	fun getHours() = getWallParts().hour
	fun getMinutes() = getWallParts().minute
	fun getSeconds() = getWallParts().second
	fun getMilliseconds() = Math.floorMod(time, 1000)
	fun getTime() = time

	fun setTime(t: Long): Long { time = t; reset(); return getTime() }

	fun setMilliseconds(ms: Int): Long {
		val wall = getWallParts()
		time = wallToUtc(wall.year, wall.month, wall.day, wall.hour, wall.minute, wall.second, ms)
		reset(); return getTime()
	}

	@JvmOverloads
	fun setSeconds(sec: Int, ms: Int = getWallParts().ms): Long {
		val wall = getWallParts()
		time = wallToUtc(wall.year, wall.month, wall.day, wall.hour, wall.minute, sec, ms)
		reset(); return getTime()
	}

	@JvmOverloads
	fun setMinutes(min: Int, sec: Int = getWallParts().second, ms: Int = getWallParts().ms): Long {
		val wall = getWallParts()
		time = wallToUtc(wall.year, wall.month, wall.day, wall.hour, min, sec, ms)
		reset(); return getTime()
	}

	@JvmOverloads
	fun setHours(hours: Int, min: Int = getWallParts().minute, sec: Int = getWallParts().second, ms: Int = getWallParts().ms): Long {
		val wall = getWallParts()
		time = wallToUtc(wall.year, wall.month, wall.day, hours, min, sec, ms)
		reset(); return getTime()
	}

	fun setDate(date: Int): Long {
		val j = toJalali(); normalizeDate(date - j.day); reset(); return getTime()
	}

	@JvmOverloads
	fun setMonth(month: Int, date: Int? = null): Long {
		var j = toJalali()
		normalizeJalali(j, j.year, month + 1, j.day); reset()

		if (date != null) { j = toJalali(); normalizeDate(date - j.day); reset() }

		return getTime()
	}

	@JvmOverloads
	fun setFullYear(year: Int, month: Int? = null, date: Int? = null): Long {
		var j = toJalali()
		normalizeJalali(j, year, if (month != null) month + 1 else j.month, j.day); reset()

		if (date != null) { j = toJalali(); normalizeDate(date - j.day); reset() }

		return getTime()
	}

	public override fun clone(): JalaliX {
		val output = JalaliX(time)
		output.timezone = timezone

		return output
	}

	fun getTimezone() = timezone

	fun getWeekOfYear(): Int {
		val dayOfYear = getDayOfYear()
		val dayOfWeek = JalaliX(time).setTimezone(timezone).getStartOfYear().getDayISO()

		return ceil((dayOfYear + dayOfWeek - 1) / 7.0).toInt()
	}

	fun getDayOfYear(): Int {
		val j = toJalali()

		return when {
			j.month < 7 -> j.day + (j.month - 1) * 31
			j.month < 12 -> j.day + 186 + (j.month - 7) * 30
			else -> j.day + 336
		}
	}

	fun getDayISO() = ((getDay() + 6) % 7) + 1
	fun getHoursISO(): Int { val h = getHours(); return if (h > 12) h - 12 else h }
	fun getMeridiem() = if (toJalali().hours > 12) pm else am
	private fun getGregorianMeridiem() = if (getHours() >= 12) pmGregorian else amGregorian

	fun getStartOfYear() = addDays(-1 * getDayOfYear() + 1).getStartOfDay()
	fun getStartOfMonth() = addDays(-1 * getDate() + 1).getStartOfDay()
	fun getStartOfWeek() = addDays(-1 * getDayISO() + 1).getStartOfDay()
	fun getStartOfDay(): JalaliX { setHours(0, 0, 0, 0); return this }

	fun getEndOfYear(): JalaliX {
		val lastDay = if (toJalali().isLeapYear) 366 else 365

		return addDays(lastDay - getDayOfYear()).getEndOfDay()
	}

	fun getEndOfMonth(): JalaliX {
		val j = toJalali()
		var lastDay = 31

		if (j.month in 7..11) lastDay = 30
		else if (j.month == 12) lastDay = if (j.isLeapYear) 30 else 29

		return addDays(lastDay - getDate()).getEndOfDay()
	}

	fun getEndOfWeek() = addDays(7 - getDayISO()).getEndOfDay()
	fun getEndOfDay(): JalaliX { setHours(23, 59, 59, 999); return this }

	fun setTimezone(timezone: String?): JalaliX {
		val output = JalaliX(getTime())
		output.timezone = normalizeTimezone(timezone)

		return output
	}

	fun addYears(year: Int): JalaliX {
		val j = toJalali(); normalizeJalali(j, j.year + year, j.month, j.day); reset(); return this
	}

	fun addMonths(month: Int): JalaliX {
		val j = toJalali(); normalizeJalali(j, j.year, j.month + month, j.day); reset(); return this
	}

	fun addWeeks(weeks: Int): JalaliX { normalizeDate(weeks * 7); reset(); return this }
	fun addDays(days: Int): JalaliX { normalizeDate(days); reset(); return this }

	@JvmOverloads
	fun toISODate(calendar: String = "gregorian"): String {
		if (calendar == "jalali") return format("YYYY-MM-DD")

		val wall = getWallParts()

		return "${wall.year}-${addZero(wall.month)}-${addZero(wall.day)}"
	}

	@JvmOverloads
	fun format(str: String, persianDigits: Boolean = false, gregorian: Boolean = false): String =
		formatInternal(str, persianDigits, gregorian)

	fun format(str: String, options: JalaliFormatOptions): String =
		formatInternal(str, options.persianDigits, options.calendar == "gregorian")

	private fun formatInternal(str: String, persian: Boolean, useGregorian: Boolean): String {
		val placeholders = mutableListOf<String>()

		fun ph(val_: String): String {
			placeholders.add(val_); return "\uE000${placeholders.lastIndex}\uE001"
		}

		var output = str
		val wall = getWallParts()
		val year = if (useGregorian) wall.year else getFullYear()
		val month = if (useGregorian) wall.month else getMonth()
		val weekOfYear = if (useGregorian) getGregorianWeekOfYear() else getWeekOfYear()
		val dayOfYear = if (useGregorian) getGregorianDayOfYear() else getDayOfYear()
		val date = if (useGregorian) wall.day else getDate()
		val monthName = if (useGregorian) getGregorianMonthName(month) else getJalaliMonthName(month)
		val weekDayName = if (useGregorian) getGregorianWeekDayName() else getWeekDayName()
		val weekDayLetter = if (useGregorian) getGregorianWeekDayLetter() else getWeekDayLetter()
		val meridiem = if (useGregorian) getGregorianMeridiem() else getMeridiem()
		val yearStr = year.toString()

		if (output.contains("YYYY")) output = output.replace("YYYY", ph(yearStr))
		if (output.contains("YYY")) output = output.replace("YYY", ph(yearStr.takeLast(3)))
		if (output.contains("YY")) output = output.replace("YY", ph(yearStr.takeLast(2)))
		if (output.contains("Y")) output = output.replace("Y", ph(yearStr.takeLast(1)))
		if (output.contains("MMM")) output = output.replace("MMM", ph(monthName))
		if (output.contains("MM")) output = output.replace("MM", ph(addZero(month)))
		if (output.contains("M")) output = output.replace("M", ph(month.toString()))
		if (output.contains("W")) output = output.replace("W", ph(addZero(weekOfYear)))
		if (output.contains("w")) output = output.replace("w", ph(weekOfYear.toString()))
		if (output.contains("DW")) output = output.replace("DW", ph(getDayISO().toString()))

		if (output.contains("dw")) {
			val dw = if (useGregorian) (if (getWallWeekday() == 0) 7 else getWallWeekday()) else getDay()
			output = output.replace("dw", ph(dw.toString()))
		}

		if (output.contains("WN")) output = output.replace("WN", ph(weekDayName))
		if (output.contains("wn")) output = output.replace("wn", ph(weekDayName))
		if (output.contains("WL")) output = output.replace("WL", ph(weekDayLetter))
		if (output.contains("wl")) output = output.replace("wl", ph(weekDayLetter))
		if (output.contains("DDDD")) output = output.replace("DDDD", ph(addZero(dayOfYear)))
		if (output.contains("DDD")) output = output.replace("DDD", ph(dayOfYear.toString()))
		if (output.contains("DD")) output = output.replace("DD", ph(addZero(date)))
		if (output.contains("D")) output = output.replace("D", ph(date.toString()))

		val hours = getHours()

		if (output.contains("HH")) output = output.replace("HH", ph(addZero(hours)))
		if (output.contains("H")) output = output.replace("H", ph(hours.toString()))

		val hoursISO = when { hours > 12 -> hours - 12; hours == 0 -> 12; else -> hours }

		if (output.contains("hh")) output = output.replace("hh", ph(addZero(hoursISO)))
		if (output.contains("h")) output = output.replace("h", ph(hoursISO.toString()))
		if (output.contains("mm")) output = output.replace("mm", ph(addZero(getMinutes())))
		if (output.contains("m")) output = output.replace("m", ph(getMinutes().toString()))
		if (output.contains("ss")) output = output.replace("ss", ph(addZero(getSeconds())))
		if (output.contains("s")) output = output.replace("s", ph(getSeconds().toString()))
		if (output.contains("sss")) output = output.replace("sss", ph(getMilliseconds().toString()))
		if (output.contains("SSS")) output = output.replace("SSS", ph(getMilliseconds().toString()))
		if (output.contains("A")) output = output.replace("A", ph(meridiem))
		if (output.contains("a")) output = output.replace("a", ph(meridiem))
		if (output.contains("X")) output = output.replace("X", ph((getTime() / 1000).toString()))
		if (output.contains("x")) output = output.replace("x", ph(getTime().toString()))

		placeholders.forEachIndexed { i, v -> output = output.replace("\uE000$i\uE001", v) }

		return if (persian) toPersian(output) else output
	}
}
