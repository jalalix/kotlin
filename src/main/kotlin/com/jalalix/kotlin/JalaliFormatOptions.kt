package com.jalalix.kotlin

// Options for JalaliX.format.
data class JalaliFormatOptions(
        var calendar: String? = null,
        var persianDigits: Boolean = false,
) {
    companion object {
        @JvmStatic fun persianDigits() = JalaliFormatOptions(persianDigits = true)
        @JvmStatic fun gregorian() = JalaliFormatOptions(calendar = "gregorian")
    }
}
