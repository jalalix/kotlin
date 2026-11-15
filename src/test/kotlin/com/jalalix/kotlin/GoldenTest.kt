package com.jalalix.kotlin

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

// Loads testdata/golden.json and asserts parity with the TypeScript contract.
class GoldenTest {
	companion object {
		lateinit var golden: JsonNode

		@BeforeAll
		@JvmStatic
		fun load() {
			val mapper = ObjectMapper()
			val stream =
					GoldenTest::class.java.classLoader.getResourceAsStream("testdata/golden.json")
			golden =
					if (stream != null) mapper.readTree(stream)
					else mapper.readTree(File("testdata/golden.json"))
		}

		fun createFrom(spec: JsonNode): JalaliX {
			if (spec.has("unixMs")) {
				var d = JalaliX.create(spec.get("unixMs").asLong())
				if (spec.has("timezone")) d = d.setTimezone(spec.get("timezone").asText())
				return d
			}

			return JalaliX.create(spec.get("isoDate").asText())
		}
	}

	@Test
	fun calendar() {
		for (c in golden.get("calendar")) {
			val d = createFrom(c.get("create"))
			val expect = c.get("expect")
			assertEquals(
					expect.get("formatYYYYMMDD").asText(),
					d.format("YYYY/MM/DD"),
					c.get("id").asText()
			)
			assertEquals(
					expect.get("toISODateGregorian").asText(),
					d.toISODate(),
					"${c.get("id").asText()} iso g"
			)
			assertEquals(
					expect.get("toISODateJalali").asText(),
					d.toISODate("jalali"),
					"${c.get("id").asText()} iso j"
			)
		}
	}

	@Test
	fun parse() {
		for (c in golden.get("parse")) {
			val d = JalaliX.parseJalali(c.get("input").asText())
			val expect = c.get("expect")
			assertEquals(
					expect.get("toISODateGregorian").asText(),
					d.toISODate(),
					c.get("id").asText()
			)

			if (expect.has("formatYYYYMMDD")) {
				assertEquals(
						expect.get("formatYYYYMMDD").asText(),
						d.format("YYYY/MM/DD"),
						"${c.get("id").asText()} format"
				)
			}
		}
	}

	@Test
	fun format() {
		for (c in golden.get("format")) {
			if (c.has("toPersian")) {
				assertEquals(
						c.get("toPersian").get("expect").asText(),
						JalaliX.toPersian(c.get("toPersian").get("input").asText())
				)
				assertEquals(
						c.get("fromPersian").get("expect").asText(),
						JalaliX.fromPersian(c.get("fromPersian").get("input").asText())
				)
				continue
			}

			val d = createFrom(c.get("create"))
			val opts = JalaliFormatOptions()

			c.get("options")?.let { options ->
				if (options.has("persianDigits"))
						opts.persianDigits = options.get("persianDigits").asBoolean()
				if (options.has("calendar")) opts.calendar = options.get("calendar").asText()
			}

			assertEquals(
					c.get("expect").asText(),
					d.format(c.get("pattern").asText(), opts),
					c.get("id").asText()
			)
		}
	}

	@Test
	fun timezone() {
		for (c in golden.get("timezone")) {
			if (c.has("setTimezoneAlias")) {
				val d =
						JalaliX.create(c.get("unixMs").asLong())
								.setTimezone(c.get("setTimezoneAlias").asText())
				assertEquals(
						c.get("expectTimezone").asText(),
						d.getTimezone(),
						c.get("id").asText()
				)
				continue
			}

			val d =
					JalaliX.create(c.get("unixMs").asLong())
							.setTimezone(c.get("withTimezone").asText())
			val expect = c.get("expect")
			assertEquals(
					expect.get("jalaliSlash").asText(),
					d.format("YYYY/MM/DD"),
					"${c.get("id").asText()} jalali"
			)

			if (expect.has("jalaliIso")) {
				assertEquals(
						expect.get("jalaliIso").asText(),
						d.toISODate("jalali"),
						"${c.get("id").asText()} jalaliIso"
				)
			}

			if (expect.has("wallGregorian")) {
				assertEquals(
						expect.get("wallGregorian").asText(),
						d.toISODate(),
						"${c.get("id").asText()} wallG"
				)
			}

			assertEquals(expect.get("hours").asInt(), d.getHours(), "${c.get("id").asText()} h")
			assertEquals(expect.get("minutes").asInt(), d.getMinutes(), "${c.get("id").asText()} m")

			if (expect.has("getTime")) {
				assertEquals(
						expect.get("getTime").asLong(),
						d.getTime(),
						"${c.get("id").asText()} instant"
				)
			}
		}
	}

	@Test
	fun mutations() {
		for (c in golden.get("mutations")) {
			var d = createFrom(c.get("create"))

			if (c.has("setFullYear")) {
				val sfy = c.get("setFullYear")
				d.setFullYear(
						sfy.get("year").asInt(),
						sfy.get("month0").asInt(),
						sfy.get("day").asInt()
				)
				assertEquals(
						c.get("expect").get("getMonth").asInt(),
						d.getMonth(),
						"${c.get("id").asText()} getMonth"
				)
				assertEquals(
						c.get("expect").get("formatYYYYMMDD").asText(),
						d.format("YYYY/MM/DD"),
						"${c.get("id").asText()} format"
				)
				continue
			}

			for (opNode in c.get("ops")) {
				val op = opNode.asText()

				when {
					op.startsWith("addMonths:") -> d = d.addMonths(op.split(":")[1].toInt())
					op == "getStartOfMonth" -> d = d.getStartOfMonth()
				}
			}

			assertEquals(
					c.get("expect").get("formatYYYYMMDD").asText(),
					d.format("YYYY/MM/DD"),
					c.get("id").asText()
			)
		}
	}

	@Test
	fun smokeCompareClone() {
		val a = JalaliX.create("2025-02-25")
		val b = JalaliX.create("2025-02-26")
		assertTrue(JalaliX.compare(a, b))
		val c = a.clone()
		assertEquals(a.getTime(), c.getTime())
		assertEquals(a.getTimezone(), c.getTimezone())
	}
}
