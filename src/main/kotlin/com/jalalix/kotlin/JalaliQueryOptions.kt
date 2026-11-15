package com.jalalix.kotlin

/** Options for [JalaliX.normalizeQueryDate]. */
data class JalaliQueryOptions(
	var timezone: String? = null,
	var jalaliYearMin: Int? = null,
	var jalaliYearMax: Int? = null,
) {
	companion object {
		@JvmStatic fun ofTimezone(timezone: String) = JalaliQueryOptions(timezone = timezone)

		@JvmStatic
		fun ofYearBand(jalaliYearMin: Int, jalaliYearMax: Int) =
			JalaliQueryOptions(jalaliYearMin = jalaliYearMin, jalaliYearMax = jalaliYearMax)
	}
}
