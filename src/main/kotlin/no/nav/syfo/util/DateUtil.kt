package no.nav.syfo.util

import java.time.*
import java.time.format.DateTimeFormatter
import java.util.*

val defaultZoneOffset: ZoneOffset = ZoneOffset.UTC

fun nowUTC(): OffsetDateTime = OffsetDateTime.now(defaultZoneOffset)

fun LocalDateTime.toOffsetDateTimeUTC(): OffsetDateTime =
    this.atZone(ZoneId.of("Europe/Oslo")).withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime()

fun LocalDate.isBeforeOrEqual(date: LocalDate) = !this.isAfter(date)

fun LocalDate.isAfterOrEqual(date: LocalDate) = !this.isBefore(date)

// Grace periode is the period from 4 months ago to today where brev can be displayed to NL by default.
fun getGracePeriodStartDate() = LocalDate.now().minusMonths(4)

fun LocalDateTime.toReadableString(): String =
    this.format(DateTimeFormatter.ofPattern("dd. MMMM yyyy", Locale("no")))
