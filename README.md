# JalaliX - Kotlin

Jalali (Persian / Shamsi) calendar for Kotlin / JVM 17+. Pure `java.time` (`ZoneId`), no runtime dependencies beyond the JDK + Kotlin stdlib.

[![license](https://img.shields.io/badge/license-MIT-blue.svg)](./LICENSE)

```kotlin
import com.jalalix.kotlin.JalaliX
import com.jalalix.kotlin.JalaliFormatOptions

val date = JalaliX.create("2025-02-25")

date.format("YYYY/MM/DD") // 1403/12/07
date.format("DD MMM YYYY", JalaliFormatOptions.persianDigits()) // ۰۷ اسفند ۱۴۰۳

JalaliX.parseJalali("1403/12/07").toISODate() // 2025-02-25
```

`toISODate()` uses wall-clock getters in the active timezone.

## Install

Coordinates: `com.jalalix.kotlin:jalalix:0.1.0`

```kotlin
implementation("com.jalalix.kotlin:jalalix:0.1.0")
```

```bash
./gradlew test
./gradlew publishToMavenLocal
./gradlew publishToMavenCentral
```

## Usage

### create / format

```kotlin
val now = JalaliX.create(System.currentTimeMillis(), JalaliOptions.ofTimezone("Asia/Tehran"))

now.format("YYYY/MM/DD")
now.format("WN، DD MMM YYYY")
now.format("YYYY/MM/DD", persianDigits = true)
now.format("DD MMM YYYY", JalaliFormatOptions.gregorian())
```

`format` overloads: `format(str, persianDigits, gregorian)` or `format(str, JalaliFormatOptions)`.

### parseJalali

```kotlin
JalaliX.parseJalali("1403/12/07")
JalaliX.parseJalali("۱۴۰۳-۱۲-۰۷ 14:30")
JalaliX.parseJalali("1403.12.07")
```

Named `parseJalali` because other APIs already own `parse`. Accepts `/`, `-`, or `.` as the date separator.

### normalizeQueryDate

Soft-normalize loose API/query date values to a Gregorian `YYYY-MM-DD HH:mm:ss` string (or `null`). Does not throw on junk input.

```kotlin
JalaliX.normalizeQueryDate("1403/12/07", JalaliQueryOptions.ofTimezone("Asia/Tehran"))
// "2025-02-25 00:00:00"

JalaliX.normalizeQueryDate("undefined") // null
JalaliX.normalizeQueryDate(1740430800000L, JalaliQueryOptions.ofTimezone("Asia/Tehran"))
// "2025-02-25 00:30:00"
```

Handles sentinels, unix timestamps (seconds or milliseconds; non-positive → `null`), Persian/Arabic-Indic digits, `/` `-` `.` separators, fractional seconds, optional `Z` / offset suffixes.

Detects Jalali when the year is in `jalaliYearMin`…`jalaliYearMax` (default `1`…`1899`) **and** the civil date is valid; otherwise Gregorian when valid, or returns the digit-cleaned string. Non-string/non-number values pass through unchanged.

### toISODate

```kotlin
val d = JalaliX.create("2025-02-25T00:30:00+03:30")

d.toISODate() // 2025-02-25
d.toISODate("jalali") // 1403-12-07
```

### add / start / end

```kotlin
JalaliX.create("2025-02-25").addMonths(1).getStartOfMonth().format("YYYY/MM/DD")
// e.g. 1404/01/01

val d = JalaliX.create("2025-02-25", JalaliOptions.ofTimezone("Asia/Tehran"))
d.addDays(7).format("YYYY/MM/DD")
d.getEndOfWeek().format("YYYY/MM/DD")
```

### Persian / Arabic digits

```kotlin
JalaliX.toPersian("1403/12/07") // ۱۴۰۳/۱۲/۰۷
JalaliX.fromPersian("۱۴۰۳/۱۲/۰۷") // 1403/12/07
JalaliX.fromPersian("١٤٠٣/١٢/٠٧") // 1403/12/07 (Arabic-Indic)
```

## Timezone

Internal storage is always a **UTC Unix millisecond instant** plus an IANA timezone id. `setTimezone` is **instant-preserving**: the timestamp stays the same; only the zone (and therefore wall clock / Jalali day) changes.

```kotlin
val d = JalaliX.create(1740430800000L)

val tehran = d.setTimezone("Asia/Tehran")
tehran.format("YYYY/MM/DD") // 1403/12/07
tehran.format("HH:mm") // 00:30

val utc = d.setTimezone("UTC")
utc.format("YYYY/MM/DD") // 1403/12/06
utc.format("HH:mm") // 21:00

tehran.getTime() == utc.getTime() // true
```

- **Default zone:** `UTC`
- **Aliases:** `utc` / `gmt` → `UTC`; `local` / `default` / `system` → `ZoneId.systemDefault()`
- **Invalid zone** → `IllegalArgumentException`

```kotlin
JalaliX.create(System.currentTimeMillis(), JalaliOptions.ofTimezone("Asia/Tehran"))
```

## Months

Easy to get wrong:

- `getMonth()` → **1…12** (Farvardin = 1)
- `setMonth(m)` / `setFullYear(y, m)` → **0…11**, same as `Date` (Farvardin = 0)

```kotlin
val d = JalaliX.create(System.currentTimeMillis())
d.setFullYear(1404, 0, 1) // 1 Farvardin 1404
d.getMonth() // 1
```

## Format tokens

| Token               | Meaning                         |
| ------------------- | ------------------------------- |
| `YYYY` `YY`         | year                            |
| `MMM` `MM` `M`      | month name / number             |
| `DD` `D`            | day                             |
| `HH` `hh` `mm` `ss` | time                            |
| `A` `a`             | meridiem (ق.ظ. / ب.ظ. or AM/PM) |
| `WN` `WL`           | weekday name / letter           |
| `w` `DDD`           | week of year / day of year      |
| `X` `x`             | unix seconds / ms               |

## API

Option types: `JalaliOptions` (`timezone`), `JalaliQueryOptions` (`timezone`, `jalaliYearMin`, `jalaliYearMax`), `JalaliFormatOptions` (`calendar`, `persianDigits`).

### Constructors

| API                  | Description                                                 |
| -------------------- | ----------------------------------------------------------- |
| `JalaliX()`          | Current instant in UTC.                                     |
| `JalaliX(epochMs)`   | From Unix epoch milliseconds.                               |
| `JalaliX(isoOrDate)` | From ISO-8601 / `YYYY-MM-DD` string.                        |
| `JalaliX(instant)`   | From `java.time.Instant`.                                   |
| `JalaliX(temporal)`  | From any `TemporalAccessor` convertible via `Instant.from`. |

### Static (`JalaliX` companion)

| API                                   | Description                                                                                                  |
| ------------------------------------- | ------------------------------------------------------------------------------------------------------------ |
| `persianNumbers`                      | Array of Persian digit characters `۰`…`۹`.                                                                   |
| `arabicNumbers`                       | Array of Arabic-Indic digit characters `٠`…`٩`.                                                              |
| `create(date, options?)`              | Build from unix ms, ISO string, `JalaliX`, or temporal; optional `JalaliOptions`.                            |
| `parseJalali(value, options?)`        | Parse a Jalali date/time string (`/` `-` `.`, Persian/Arabic digits, optional time).                         |
| `normalizeQueryDate(value, options?)` | Soft-normalize loose query values → Gregorian `YYYY-MM-DD HH:mm:ss`, cleaned string, passthrough, or `null`. |
| `isValidDate(date)`                   | `true` if the value is a supported date type.                                                                |
| `toPersian(str)`                      | Replace Western digits `0-9` with Persian `۰-۹`.                                                             |
| `fromPersian(str)`                    | Replace Persian `۰-۹` and Arabic-Indic `٠-٩` with Western digits.                                            |
| `compare(date, comparing)`            | `true` if the first date is strictly before the second (by unix ms).                                         |

### Instance - getters

| API                 | Description                                                           |
| ------------------- | --------------------------------------------------------------------- |
| `getFullYear()`     | Jalali year in the active timezone.                                   |
| `getMonth()`        | Jalali month **1…12** (Farvardin = 1).                                |
| `getDate()`         | Jalali day of month **1…31**.                                         |
| `getDay()`          | Weekday **1…7** (Saturday = 1 … Friday = 7).                          |
| `getHours()`        | Wall-clock hours in the active timezone.                              |
| `getMinutes()`      | Wall-clock minutes.                                                   |
| `getSeconds()`      | Wall-clock seconds.                                                   |
| `getMilliseconds()` | Wall-clock milliseconds.                                              |
| `getTime()`         | Unix timestamp in milliseconds (UTC instant).                         |
| `getTimezone()`     | Active IANA timezone id.                                              |
| `getWeekOfYear()`   | Jalali week-of-year (1-based).                                        |
| `getDayOfYear()`    | Day of Jalali year (1…365/366).                                       |
| `getDayISO()`       | ISO-style weekday index used internally for week math.                |
| `getHoursISO()`     | Hours in 0…23 for formatting helpers.                                 |
| `getMeridiem()`     | Localized meridiem string (ق.ظ./ب.ظ. or AM/PM depending on calendar). |

### Instance - setters

| API                                | Description                                              |
| ---------------------------------- | -------------------------------------------------------- |
| `setTime(t)`                       | Set the UTC instant from unix milliseconds.              |
| `setMilliseconds(ms)`              | Set milliseconds (wall clock).                           |
| `setSeconds(sec, ms)`              | Set seconds and optional milliseconds.                   |
| `setMinutes(min, sec, ms)`         | Set minutes and optional lower units.                    |
| `setHours(hours, min, sec, ms)`    | Set hours and optional lower units.                      |
| `setDate(date)`                    | Set Jalali day of month.                                 |
| `setMonth(month, date?)`           | Set Jalali month **0…11** (Farvardin = 0); optional day. |
| `setFullYear(year, month?, date?)` | Set Jalali year; optional month **0…11** and day.        |
| `setTimezone(timezone)`            | Instant-preserving zone change; returns a new instance.  |

### Instance - math, range, format

| API                                     | Description                                            |
| --------------------------------------- | ------------------------------------------------------ |
| `addYears(year)`                        | Add calendar years (mutates / returns `this`).         |
| `addMonths(month)`                      | Add calendar months.                                   |
| `addWeeks(weeks)`                       | Add weeks.                                             |
| `addDays(days)`                         | Add days.                                              |
| `getStartOfYear()`                      | Start of Jalali year (mutates / returns `this`).       |
| `getStartOfMonth()`                     | Start of Jalali month.                                 |
| `getStartOfWeek()`                      | Start of week.                                         |
| `getStartOfDay()`                       | Start of day.                                          |
| `getEndOfYear()`                        | End of Jalali year.                                    |
| `getEndOfMonth()`                       | End of Jalali month.                                   |
| `getEndOfWeek()`                        | End of week.                                           |
| `getEndOfDay()`                         | End of day.                                            |
| `format(str, persianDigits, gregorian)` | Format with tokens and optional flags.                 |
| `format(str, options)`                  | Format with `JalaliFormatOptions`.                     |
| `toISODate(calendar)`                   | `YYYY-MM-DD` in `"gregorian"` (default) or `"jalali"`. |
| `clone()`                               | Copy with the same instant and timezone.               |
| `toString()`                            | Human-readable Jalali date/time string.                |
| `toDateString()`                        | Jalali weekday + date string.                          |
| `toLocaleDateString()`                  | `YYYY/MM/DD` in Jalali.                                |
| `toLocaleTimeString()`                  | 12-hour time with meridiem.                            |

### Option helpers

| API                                       | Description                                              |
| ----------------------------------------- | -------------------------------------------------------- |
| `JalaliOptions.ofTimezone(timezone)`      | Options with IANA timezone for `create` / `parseJalali`. |
| `JalaliQueryOptions.ofTimezone(timezone)` | Query options with timezone.                             |
| `JalaliQueryOptions.ofYearBand(min, max)` | Query options with Jalali year band.                     |
| `JalaliFormatOptions.persianDigits()`     | Format with Persian digits.                              |
| `JalaliFormatOptions.gregorian()`         | Format using Gregorian calendar tokens.                  |

## Links

- https://jalalix.com
- https://github.com/jalalix/kotlin
- [CHANGELOG.md](./CHANGELOG.md)

## License

MIT
