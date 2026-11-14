# JalaliX (Kotlin)

Jalali (Persian / Shamsi) calendar for Kotlin / JVM 17+. Pure `java.time` (`ZoneId`), no runtime dependencies beyond the JDK + Kotlin stdlib.

```kotlin
import com.jalalix.kotlin.JalaliX
import com.jalalix.kotlin.JalaliFormatOptions

val date = JalaliX.create("2025-02-25")

date.format("YYYY/MM/DD") // 1403/12/07
date.format("DD MMM YYYY", JalaliFormatOptions.persianDigits()) // ۰۷ اسفند ۱۴۰۳

JalaliX.parseJalali("1403/12/07").toISODate() // 2025-02-25
```

## Install

Coordinates: `com.jalalix.kotlin:jalalix:0.1.0`

```bash
./gradlew test
./gradlew publishToMavenLocal
```

## Timezone

Internal storage is a **UTC Unix millisecond instant** plus an IANA timezone id. `setTimezone` is **instant-preserving**.

- **Default zone:** `UTC`
- **Aliases:** `utc` / `gmt` -> `UTC`; `local` / `default` / `system` -> `ZoneId.systemDefault()`
- **Invalid zone** -> `IllegalArgumentException`

## Months

- `getMonth()` -> 1..12 (Farvardin = 1)
- `setMonth(m)` / `setFullYear(y, m)` -> 0..11 (Farvardin = 0)

## License

MIT
