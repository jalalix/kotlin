package com.jalalix.kotlin

// Options for JalaliX.create / JalaliX.parseJalali.
data class JalaliOptions(var timezone: String? = null) {
	companion object {
		@JvmStatic fun ofTimezone(timezone: String) = JalaliOptions(timezone)
	}
}
